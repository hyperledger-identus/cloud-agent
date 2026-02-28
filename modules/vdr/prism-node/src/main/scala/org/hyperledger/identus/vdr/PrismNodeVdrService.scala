package org.hyperledger.identus.vdr

import fmgp.did.method.prism.RefVDR
import hyperledger.identus.vdr.prism
import interfaces.Proof
import io.grpc.StatusRuntimeException
import io.iohk.atala.prism.protos.{node_api, node_models}
import org.hyperledger.identus.shared.models.{HexString, WalletAccessContext}
import urlManagers.BaseUrlManager
import zio.*

class PrismNodeVdrService(
    client: PrismNodeClient,
    signer: VdrOperationSigner,
    urlManager: BaseUrlManager
) extends VdrService {
  override val identifier: String = "prism-node"
  override val version: String = "1.0.0"
  private val family: String = "prism"
  private val ops = PrismVdrLogic(client)

  private def mapStatusError(
      e: StatusRuntimeException
  ): VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound =
    ops.mapStatusError(e)

  private def extractHash(url: String): String =
    ops.extractHash(url)

  private def logRequest(name: String, payload: String): UIO[Unit] =
    ops.logRequest(name, payload)

  private def logResponse(name: String, payload: String): UIO[Unit] =
    ops.logResponse(name, payload)

  private def hexString(bytes: Array[Byte]): String = HexString.fromByteArray(bytes).toString
  private def bytesFromHex(url: String): Task[Array[Byte]] = ZIO.fromTry(HexString.fromString(url)).map(_.toByteArray)

  private def mapCreateOutput(out: node_api.OperationOutput, options: VdrOptions): VdrOperationResult = {
    val mutableFlag = options.get("m").getOrElse("0")
    val fragment = out.result match {
      case node_api.OperationOutput.Result.CreateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case node_api.OperationOutput.Result.UpdateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case node_api.OperationOutput.Result.DeactivateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case _ => hexString(out.getOperationId.toByteArray)
    }
    // Compose URL per VDR spec
    import scala.jdk.CollectionConverters.*
    val url = urlManager.create(
      Array.empty,
      Map(
        "drf" -> family,
        "drid" -> identifier,
        "drv" -> version,
        "m" -> mutableFlag
      ).asJava,
      fragment,
      null
    )
    VdrOperationResult(url, Some(hexString(out.getOperationId.toByteArray)))
  }

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.DriverNotFound | VdrServiceError.MissingVdrKey | VdrServiceError.DeactivatedDid,
    VdrOperationResult
  ] =
    for {
      _ <- logRequest("create", s"bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}")
      signed <- signer.signCreate(data, didKeyId)
      out <- ops.scheduleSingle(signed, "createVdrEntry").mapError {
        case d: VdrServiceError.DriverNotFound    => d
        case nf: VdrServiceError.VdrEntryNotFound => VdrServiceError.DriverNotFound(nf.cause)
      }
      _ <- logResponse("create", s"output=$out")
    } yield mapCreateOutput(out, options)

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey |
      VdrServiceError.DeactivatedDid,
    Option[VdrOperationResult]
  ] =
    for {
      entryIdHex <- ZIO.succeed(extractHash(url))
      head <- ops.fetchLatestHead(entryIdHex)
      previous = head.eventHash.toByteArray
      _ <- logRequest(
        "update",
        s"entryId=$entryIdHex, head=${HexString.fromByteArray(previous)}, bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signUpdate(previous, data, didKeyId)
      out <- ops.scheduleSingle(signed, "updateVdrEntry")
      _ <- logResponse("update", s"output=$out")
      opIdHex = hexString(out.getOperationId.toByteArray)
    } yield Some(VdrOperationResult(url, Some(opIdHex)))

  override def read(url: VdrUrl): IO[VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound, Array[Byte]] =
    (for {
      hash <- bytesFromHex(extractHash(url))
      _ <- logRequest("read", s"url=$url hashLen=${hash.length}")
      resp <- client
        .getVdrEntry(
          node_api.GetVdrEntryRequest(
            // prism-node expects the immutable entry hash in eventHash.
            eventHash = com.google.protobuf.ByteString.copyFrom(hash)
          )
        )
        .tapError(e => ZIO.logError(s"[prism-node VDR] read error: ${e}"))
      entry = resp.getEntry
      _ <- ZIO.logDebug(
        s"[prism-node VDR] read entry metadata url=$url status=${entry.status} hash=${entry.eventHash.toByteArray.map("%02X" format _).mkString}"
      )
      _ <- ZIO
        .fail(new prism.DataAlreadyDeactivatedException(RefVDR(hexString(hash))))
        .when(entry.status == node_api.VdrEntryStatus.DEACTIVATED)
      verification <- client.verifyVdrEntry(
        node_api.VerifyVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash))
      )
      _ <- ZIO
        .fail(new RuntimeException(s"VDR entry verification failed for $url: ${verification.reason}"))
        .when(!verification.valid)
      _ <- logResponse("read", s"entryBytes=${entry.toByteArray.length}, status=${entry.status}")
      data = entry.getData
      dataBytes =
        if data.content.bytes.isDefined then data.getBytes.toByteArray
        else if data.content.ipfs.isDefined then data.getIpfs.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        else if data.content.statusListEntry.isDefined then data.getStatusListEntry.toByteArray
        else Array.emptyByteArray
    } yield dataBytes).mapError {
      case e: StatusRuntimeException                => mapStatusError(e)
      case e: prism.DataAlreadyDeactivatedException => VdrServiceError.VdrEntryNotFound(e)
      case e: Throwable                             => VdrServiceError.DriverNotFound(e)
    }

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey |
      VdrServiceError.DeactivatedDid,
    Option[String]
  ] =
    for {
      entryIdHex <- ZIO.succeed(extractHash(url))
      head <- ops.fetchLatestHead(entryIdHex)
      previous = head.eventHash.toByteArray
      _ <- logRequest(
        "delete",
        s"entryId=$entryIdHex, head=${HexString.fromByteArray(previous)}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signDeactivate(previous, didKeyId)
      out <- ops.scheduleSingle(signed, "deactivateVdrEntry")
      _ <- logResponse("delete", s"output=$out")
    } yield Some(hexString(out.getOperationId.toByteArray))

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    (for {
      hash <- bytesFromHex(url)
      _ <- logRequest("verify", s"url=$url, returnData=$returnData")
      resp <- client.verifyVdrEntry(node_api.VerifyVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash)))
      _ <- logResponse("verify", s"valid=${resp.valid}")
      proofBytes = if (resp.valid) hash else Array.emptyByteArray
    } yield Proof("prism-node", Array.emptyByteArray, proofBytes)).orDie

  override def getOperationStatus(operationId: String): IO[VdrServiceError.DriverNotFound, VdrOperationStatus] =
    (for {
      _ <- logRequest("getOperationStatus", s"operationId=$operationId")
      opIdBytes <- ZIO.fromTry(HexString.fromString(operationId)).map(_.toByteArray)
      resp <- client.getOperationInfo(
        node_api.GetOperationInfoRequest(
          com.google.protobuf.ByteString.copyFrom(opIdBytes)
        )
      )
      _ <- logResponse("getOperationStatus", s"status=${resp.operationStatus}")
      detailsOpt = Option(resp.details).filter(_.nonEmpty)
      txOpt = Option(resp.transactionId).filter(_.nonEmpty)
    } yield VdrOperationStatus(resp.operationStatus.toString, detailsOpt, txOpt)).mapError {
      case e: StatusRuntimeException => VdrServiceError.DriverNotFound(e)
      case e: Throwable              => VdrServiceError.DriverNotFound(e)
    }
}

object PrismNodeVdrService {
  def init(
      stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub,
      signer: VdrOperationSigner,
      urlManager: BaseUrlManager
  ): Task[VdrService] =
    ZIO.succeed(new PrismNodeVdrService(new PrismNodeGrpcClient(stub), signer, urlManager))
}
