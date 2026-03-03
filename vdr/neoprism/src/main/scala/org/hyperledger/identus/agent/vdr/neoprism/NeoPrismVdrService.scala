package org.hyperledger.identus.agent.vdr.neoprism

import hyperledger.identus.vdr.prism
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.agent.vdr.*
import org.hyperledger.identus.agent.vdr.VdrServiceError.*
import org.hyperledger.identus.castor.core.service.{NeoPrismClient, NeoPrismSubmissionResult, NeoPrismVdrEntryMetadata}
import org.hyperledger.identus.shared.models.{HexString, WalletAccessContext}
import interfaces.Proof
import urlManagers.BaseUrlManager
import zio.*

import scala.jdk.CollectionConverters.*

class NeoPrismVdrService(
    client: NeoPrismClient,
    signer: VdrOperationSigner,
    urlManager: BaseUrlManager
) extends VdrService {
  override val identifier: String = "neoprism"
  override val version: String = "1.0.0"
  private val family: String = "prism"

  private def hexString(bytes: Array[Byte]): String = HexString.fromByteArray(bytes).toString
  private def bytesFromHex(hex: String): Task[Array[Byte]] = ZIO.fromTry(HexString.fromString(hex)).map(_.toByteArray)

  private def extractHash(url: String): String =
    url.split("#").lastOption.getOrElse(url)

  private def logRequest(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[neoprism VDR] $name request: $payload")

  private def logResponse(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[neoprism VDR] $name response: $payload")

  private def fetchPreviousEventHash(
      entryHash: String
  ): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    client
      .getVdrEntryMetadata(entryHash)
      .mapError(e => DriverNotFound(e): DriverNotFound | VdrEntryNotFound)
      .flatMap {
        case Some(meta) => bytesFromHex(meta.latestEventHash).mapError(e => DriverNotFound(e))
        case None       => ZIO.fail(VdrEntryNotFound(new prism.DataCouldNotBeFoundException(Some(entryHash))))
      }

  private def submitSigned(
      signed: node_models.SignedAtalaOperation
  ): IO[DriverNotFound, NeoPrismSubmissionResult] = {
    val hexEncoded = hexString(signed.toByteArray)
    client
      .submitVdrOperation(Seq(hexEncoded))
      .mapError(e => DriverNotFound(e))
  }

  private def composeUrl(fragment: String, options: VdrOptions): String = {
    val mutableFlag = options.get("m").getOrElse("0")
    urlManager.create(
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
  }

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey | DeactivatedDid, VdrOperationResult] =
    for {
      _ <- logRequest("create", s"bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}")
      signed <- signer.signCreate(data, didKeyId)
      result <- submitSigned(signed)
      operationId = result.operationIds.headOption.getOrElse(result.txId)
      url = composeUrl(operationId, options)
      _ <- logResponse("create", s"txId=${result.txId}, operationIds=${result.operationIds.mkString(",")}")
    } yield VdrOperationResult(url, result.operationIds.headOption)

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey | DeactivatedDid, Option[
    VdrOperationResult
  ]] =
    for {
      entryHash <- ZIO.succeed(extractHash(url))
      previousEventHash <- fetchPreviousEventHash(entryHash)
      _ <- logRequest(
        "update",
        s"entryId=$entryHash, head=${hexString(previousEventHash)}, bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signUpdate(previousEventHash, data, didKeyId)
      result <- submitSigned(signed)
      _ <- logResponse("update", s"txId=${result.txId}, operationIds=${result.operationIds.mkString(",")}")
    } yield Some(VdrOperationResult(url, result.operationIds.headOption))

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    (for {
      entryHash <- ZIO.succeed(extractHash(url))
      _ <- logRequest("read", s"url=$url, entryHash=$entryHash")
      dataOpt <- client.getVdrBlob(entryHash)
      data <- dataOpt match
        case Some(bytes) => ZIO.succeed(bytes)
        case None        => ZIO.fail(VdrEntryNotFound(new prism.DataCouldNotBeFoundException(Some(entryHash))))
      _ <- logResponse("read", s"dataBytes=${data.length}")
    } yield data).mapError {
      case e: VdrEntryNotFound => e
      case e: Throwable        => DriverNotFound(e)
    }

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey | DeactivatedDid, Option[String]] =
    for {
      entryHash <- ZIO.succeed(extractHash(url))
      previousEventHash <- fetchPreviousEventHash(entryHash)
      _ <- logRequest(
        "delete",
        s"entryId=$entryHash, head=${hexString(previousEventHash)}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signDeactivate(previousEventHash, didKeyId)
      result <- submitSigned(signed)
      _ <- logResponse("delete", s"txId=${result.txId}, operationIds=${result.operationIds.mkString(",")}")
    } yield result.operationIds.headOption

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    ZIO.succeed(Proof("neoprism", Array.emptyByteArray, Array.emptyByteArray))

  override def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus] =
    (for {
      _ <- logRequest("getOperationStatus", s"operationId=$operationId")
      indexed <- client.isOperationIndexed(operationId)
      status = if indexed then "CONFIRMED_AND_APPLIED" else "AWAIT_CONFIRMATION"
      _ <- logResponse("getOperationStatus", s"status=$status")
    } yield VdrOperationStatus(status, None, None)).mapError(e => DriverNotFound(e))
}

object NeoPrismVdrService {
  def init(
      client: NeoPrismClient,
      signer: VdrOperationSigner,
      urlManager: BaseUrlManager
  ): Task[VdrService] =
    ZIO.succeed(new NeoPrismVdrService(client, signer, urlManager))
}
