package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import fmgp.did.method.prism.RefVDR
import hyperledger.identus.vdr.prism
import interfaces.{Driver, Proof}
import io.grpc.StatusRuntimeException
import io.iohk.atala.prism.protos.{node_api, node_models}
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.blockfrost.BlockfrostPrismDriverProvider
import org.hyperledger.identus.agent.vdr.database.DatabaseDriverProvider
import org.hyperledger.identus.agent.vdr.memory.MemoryDriverProvider
import org.hyperledger.identus.agent.vdr.VdrConfigs.PRISMDriverConfig
import org.hyperledger.identus.agent.vdr.VdrServiceError.{DriverNotFound, MissingVdrKey, VdrEntryNotFound}
import org.hyperledger.identus.shared.models.HexString
import org.hyperledger.identus.shared.models.WalletAccessContext
import proxy.VDRProxyMultiDrivers
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException
import urlManagers.BaseUrlManager
import zio.*

import scala.jdk.CollectionConverters.*

class VdrServiceImpl(
    proxy: Option[VDRProxyMultiDrivers],
    prismNodeService: Option[VdrService],
    override val identifier: String,
    override val version: String
) extends VdrService {

  extension [R, A](z: ZIO[R, Throwable, A]) {
    def refineVdrError: ZIO[R, DriverNotFound | VdrEntryNotFound, A] =
      z.refineOrDie[DriverNotFound | VdrEntryNotFound] {
        case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
        case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        // Errors thrown from prism-vdr-driver are wrapped in ZIO FiberFailure context
        case FiberFailure(Cause.Die(e: prism.DataAlreadyDeactivatedException, _)) => VdrEntryNotFound(e)
        case FiberFailure(Cause.Die(e: prism.DataNotInitializedException, _))     => VdrEntryNotFound(e)
        case FiberFailure(Cause.Die(e: prism.DataCouldNotBeFoundException, _))    => VdrEntryNotFound(e)
      }
  }

  private def proxyUrl(url: String): Boolean = url.startsWith("vdr://")
  private def wantPrismNode(options: VdrOptions): Boolean =
    options.get("drid").contains("prism-node") || options.get("drf").contains("prism-node")
  private def isPrismNodeUrl(url: String): Boolean =
    url.contains("drid=prism-node") || url.contains("drf=prism")

  private def mapProxyError(th: Throwable): DriverNotFound | VdrEntryNotFound = th match
    case e: NoDriverWithThisSpecificationsException                           => DriverNotFound(e)
    case e: InMemoryDriver.DataCouldNotBeFoundException                       => VdrEntryNotFound(e)
    case e: DatabaseDriver.DataCouldNotBeFoundException                       => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataAlreadyDeactivatedException, _)) => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataNotInitializedException, _))     => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataCouldNotBeFoundException, _))    => VdrEntryNotFound(e)
    case e: Throwable                                                         => DriverNotFound(e)

  private def useProxy[A](f: VDRProxyMultiDrivers => A): IO[DriverNotFound | VdrEntryNotFound, A] =
    proxy match
      case Some(p) =>
        ZIO
          .attemptBlocking(f(p))
          .mapError(mapProxyError)
      case None =>
        ZIO.fail(DriverNotFound(new NoDriverWithThisSpecificationsException(null, null, Array.empty)))

  private def orDieProxy[A](zio: IO[DriverNotFound | VdrEntryNotFound, A]): UIO[A] =
    zio
      .mapError(e =>
        new RuntimeException(
          e.userFacingMessage,
          e match
            case DriverNotFound(cause)   => cause
            case VdrEntryNotFound(cause) => cause
        )
      )
      .orDie

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrOperationResult] =
    if (wantPrismNode(options)) {
      prismNodeService match
        case Some(s) => s.create(data, options, didKeyId)
        case None    =>
          ZIO.fail(DriverNotFound(new NoDriverWithThisSpecificationsException("prism-node", null, Array.empty)))
    } else {
      useProxy(_.create(data, options.asJava)).map(url => VdrOperationResult(url, None)).mapError {
        case d: DriverNotFound   => d
        case _: VdrEntryNotFound => DriverNotFound(new RuntimeException("No driver found for create"))
      }
    }

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrOperationResult]] =
    if (prismNodeService.nonEmpty && (!proxyUrl(url) || isPrismNodeUrl(url)))
      prismNodeService.get.update(data, url, options, didKeyId)
    else
      useProxy(p => Option(p.update(data, url, options.asJava))).map(_.map(VdrOperationResult(_, None)))

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    if (prismNodeService.nonEmpty && (!proxyUrl(url) || isPrismNodeUrl(url)))
      prismNodeService.get.read(url)
    else
      useProxy(_.read(url))

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[String]] =
    if (prismNodeService.nonEmpty && (!proxyUrl(url) || isPrismNodeUrl(url)))
      prismNodeService.get.delete(url, options, didKeyId)
    else
      useProxy(_.delete(url, options.asJava)).as(None)

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    if (prismNodeService.nonEmpty && !proxyUrl(url))
      prismNodeService.get.verify(url, returnData)
    else
      orDieProxy(useProxy(_.verify(url, returnData)))

  override def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus] =
    prismNodeService match
      case Some(s) => s.getOperationStatus(operationId)
      case None    => ZIO.fail(DriverNotFound(new RuntimeException("Operation status available only for prism-node")))

}

object VdrServiceImpl {
  final case class Config(
      enableInMemoryDriver: Boolean,
      enableDatabaseDriver: Boolean,
      prismDriver: Option[PRISMDriverConfig],
      prismNodeDriver: Option[PrismNodeDriverConfig]
  )

  final case class PrismNodeDriverConfig(
      host: String,
      port: Int,
      usePlainText: Boolean
  )

  def layer: RLayer[
    DataSource & Config & VdrOperationSigner & node_api.NodeServiceGrpc.NodeServiceBlockingStub,
    VdrService
  ] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[Config]
        signer <- ZIO.service[VdrOperationSigner]
        stub <- ZIO.service[node_api.NodeServiceGrpc.NodeServiceBlockingStub]
        urlManager <- ZIO.attempt(BaseUrlManager.apply("vdr://", "BaseURL"))
        prismNodeSvc: Option[VdrService] <- config.prismNodeDriver match
          case Some(_) => PrismNodeVdrService.init(stub, signer, urlManager).map(Some(_))
          case None    => ZIO.none
        dbDriverDataSource <- ZIO.service[DataSource]
        maybeMemoryDriver = MemoryDriverProvider.load(config.enableInMemoryDriver)
        maybeDatabaseDriver = DatabaseDriverProvider.load(config.enableDatabaseDriver, dbDriverDataSource)
        maybePrismDriver <- config.prismDriver match
          case Some(c) => BlockfrostPrismDriverProvider.load(c).map(Some(_))
          case None    => ZIO.none
        drivers = Array(
          maybeMemoryDriver,
          maybeDatabaseDriver,
          maybePrismDriver
        ).flatten
        _ <- ZIO.logInfo(
          s"VDR driver init | memory=${config.enableInMemoryDriver}, database=${config.enableDatabaseDriver}, prism=${config.prismDriver.isDefined}, prism-node=${config.prismNodeDriver.isDefined && prismNodeSvc.isDefined}; loaded=${drivers.map(_.getIdentifier()).mkString(",")}"
        )
        proxyOpt <-
          if drivers.nonEmpty then ZIO.attempt(Some(VDRProxyMultiDrivers(urlManager, drivers, "proxy", "0.1.0")))
          else ZIO.succeed(None)
        identifier = proxyOpt.map(_.getIdentifier()).orElse(prismNodeSvc.map(_.identifier)).getOrElse("vdr")
        version = proxyOpt.map(_.getVersion()).orElse(prismNodeSvc.map(_.version)).getOrElse("0.1.0")
        service = VdrServiceImpl(
          proxyOpt,
          prismNodeSvc,
          identifier,
          version
        )
      } yield service
    }

}

/** Prism-node backed VDR service (gRPC) */
object PrismNodeVdrService {
  def init(
      stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub,
      signer: VdrOperationSigner,
      urlManager: BaseUrlManager
  ): Task[VdrService] =
    ZIO.succeed(new PrismNodeVdrService(new PrismNodeGrpcClient(stub), signer, urlManager))
}

class PrismNodeVdrService(
    client: PrismNodeClient,
    signer: VdrOperationSigner,
    urlManager: BaseUrlManager
) extends VdrService {
  override val identifier: String = "prism-node"
  override val version: String = "1.0.0"
  private val family: String = "prism"
  private val ops = PrismVdrLogic(client)

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

  private def mapStatusError(e: StatusRuntimeException): DriverNotFound | VdrEntryNotFound =
    ops.mapStatusError(e)

  private def extractHash(url: String): String =
    ops.extractHash(url)

  private def logRequest(name: String, payload: String): UIO[Unit] =
    ops.logRequest(name, payload)

  private def logResponse(name: String, payload: String): UIO[Unit] =
    ops.logResponse(name, payload)

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrOperationResult] =
    for {
      _ <- logRequest("create", s"bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}")
      signed <- signer.signCreate(data, didKeyId)
      out <- ops.scheduleSingle(signed, "createVdrEntry").mapError {
        case d: DriverNotFound    => d
        case nf: VdrEntryNotFound => DriverNotFound(nf.cause)
      }
      _ <- logResponse("create", s"output=$out")
    } yield mapCreateOutput(out, options)

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrOperationResult]] =
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

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
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
      case e: prism.DataAlreadyDeactivatedException => VdrEntryNotFound(e)
      case e: Throwable                             => DriverNotFound(e)
    }

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[String]] =
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

  override def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus] =
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
      case e: StatusRuntimeException => DriverNotFound(e)
      case e: Throwable              => DriverNotFound(e)
    }
}
