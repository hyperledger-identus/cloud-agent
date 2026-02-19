package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import fmgp.crypto.Secp256k1PrivateKey
import fmgp.did.method.prism.{
  BlockfrostConfig,
  BlockfrostRyoConfig,
  DIDPrism,
  IndexerConfig,
  PrismChainService,
  PrismChainServiceImpl,
  PrismState,
  PrismStateInMemory,
  RefVDR
}
import fmgp.did.method.prism.cardano.CardanoWalletConfig
import fmgp.did.method.prism.vdr.{Indexer, VDRService, VDRServiceImpl}
import hyperledger.identus.vdr.prism
import hyperledger.identus.vdr.prism.PRISMDriverInMemory
import interfaces.{Driver, Proof}
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.iohk.atala.prism.protos.node_api
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.VdrServiceError.{DriverNotFound, MissingVdrKey, VdrEntryNotFound}
import org.hyperledger.identus.shared.models.HexString
import org.hyperledger.identus.shared.models.WalletAccessContext
import proxy.VDRProxyMultiDrivers
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException
import urlManagers.BaseUrlManager
import zio.*

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

type VdrUrl = String
type VdrOptions = Map[String, String]
final case class VdrOperationResult(url: VdrUrl, operationId: Option[String])
final case class VdrOperationStatus(status: String, details: Option[String], transactionId: Option[String])

trait VdrService {
  def identifier: String
  def version: String

  def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrOperationResult]
  def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrOperationResult]]
  def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]]
  def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[String]]
  def verify(url: VdrUrl, returnData: Boolean = false): UIO[Proof]

  def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus]
}

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

  final case class BlockfrostPrivateNetworkConfig(
      url: String,
      protocolMagic: Int
  )

  final case class PRISMDriverConfig(
      blockfrostApiKey: Option[String],
      privateNetwork: Option[BlockfrostPrivateNetworkConfig],
      walletMnemonic: Seq[String],
      didPrism: String,
      vdrPrivateKey: Array[Byte],
      prismStateDir: String,
      indexIntervalSecond: Int
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
        maybeMemoryDriver =
          if config.enableInMemoryDriver
          then Some(InMemoryDriver("memory", "memory", "0.1.0", Array.empty))
          else None
        maybeDatabaseDriver =
          if config.enableDatabaseDriver
          then Some(DatabaseDriver("database", "0.1.0", Array.empty, dbDriverDataSource))
          else None
        maybePrismDriver <- config.prismDriver match
          case Some(c) => initPrismDriver(c).map(Some(_))
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

  private def initPrismDriver(config: PRISMDriverConfig): UIO[Driver] =
    for
      _ <- createIndexerDirectories(config.prismStateDir).orDie
      bfConfig <- (config.blockfrostApiKey, config.privateNetwork) match {
        case (Some(apiKey), None) =>
          ZIO.succeed(BlockfrostConfig(apiKey, None))
        case (None, Some(networkConfig)) =>
          ZIO.succeed(
            BlockfrostConfig(
              "", // Empty API key for private blockfrost instance
              Some(BlockfrostRyoConfig(url = networkConfig.url, protocolMagic = networkConfig.protocolMagic))
            )
          )
        case _ =>
          ZIO.die(
            new IllegalStateException(
              "Invalid blockfrost configuration: exactly one of blockfrostApiKey or privateNetwork must be provided"
            )
          )
      }
      wallet = CardanoWalletConfig(config.walletMnemonic)
      chain: PrismChainService = PrismChainServiceImpl(bfConfig, wallet)
      prismState <- PrismStateInMemory.empty
      vdrService: VDRService = VDRServiceImpl(chain, prismState)
      driver = PRISMDriverInMemory(
        vdrService = vdrService,
        didPrism = DIDPrism(config.didPrism.replace("did:prism:", "")),
        vdrKey = Secp256k1PrivateKey(config.vdrPrivateKey),
      )
      indexerConfig: IndexerConfig =
        IndexerConfig(mBlockfrostConfig = Some(bfConfig), workdir = config.prismStateDir)
      _ <- Indexer.indexerJobFS
        .tap(_ => PRISMDriverInMemory.loadPrismStateFromChunkFiles)
        .schedule(Schedule.spaced(config.indexIntervalSecond.seconds))
        .provideSome[PrismState](ZLayer.succeed(indexerConfig))
        .provideEnvironment(ZEnvironment(prismState))
        .fork
    yield driver

  private def createIndexerDirectories(baseDir: String): Task[Unit] = {
    val requiredDirs = Seq("cardano-21325", "diddoc", "events", "ssi", "vdr")

    (for {
      _ <- ZIO.logInfo(s"Initializing PRISM indexer directories at: $baseDir")
      basePath <- ZIO.attemptBlocking(Paths.get(baseDir))
      _ <- ZIO.attemptBlocking {
        if (!Files.exists(basePath)) {
          Files.createDirectories(basePath)
        }
        requiredDirs.foreach { dirName =>
          val dirPath = basePath.resolve(dirName)
          if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
          }
        }
      }
      _ <- ZIO.logInfo(s"Successfully created PRISM indexer directories at: $baseDir")
    } yield ()).catchAll { e =>
      ZIO.logError(s"Failed to create PRISM indexer directories at '$baseDir': ${e.getMessage}") *>
        ZIO.fail(e)
    }
  }
}

/** Prism-node backed VDR service (gRPC) */
object PrismNodeVdrService {
  def init(
      stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub,
      signer: VdrOperationSigner,
      urlManager: BaseUrlManager
  ): Task[VdrService] =
    ZIO.succeed(new PrismNodeVdrService(stub, signer, urlManager))
}

class PrismNodeVdrService(
    stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub,
    signer: VdrOperationSigner,
    urlManager: BaseUrlManager
) extends VdrService {
  override val identifier: String = "prism-node"
  override val version: String = "1.0.0"
  private val family: String = "prism"

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
    e.getStatus.getCode match
      case Status.Code.NOT_FOUND | Status.Code.UNKNOWN => VdrEntryNotFound(e)
      case Status.Code.FAILED_PRECONDITION             => VdrEntryNotFound(e) // e.g., latest entry is deactivated
      case _                                           => DriverNotFound(e)

  private def extractHash(url: String): String =
    url.split("#").lastOption.getOrElse(url)

  private def logRequest(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[prism-node VDR] $name request: $payload")

  private def logResponse(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[prism-node VDR] $name response: $payload")

  /** Fetch the current head for the immutable entry id (the hash in the URL). */
  private def fetchLatestHead(entryIdHex: String): IO[DriverNotFound | VdrEntryNotFound, node_api.VdrEntry] =
    for {
      entryIdBytes <- bytesFromHex(entryIdHex).mapError(DriverNotFound(_))
      _ <- logRequest("head", s"entryId=$entryIdHex")
      resp <- ZIO
        .attemptBlocking(
          // prism-node edge proto only accepts event_hash; entry_id/latest removed.
          stub.getVdrEntry(
            node_api.GetVdrEntryRequest(
              eventHash = com.google.protobuf.ByteString.copyFrom(entryIdBytes)
            )
          )
        )
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => DriverNotFound(e)
        }
      entry = resp.getEntry
      _ <- ZIO
        .fail(VdrEntryNotFound(new prism.DataAlreadyDeactivatedException(RefVDR(entryIdHex))))
        .when(entry.deactivated || entry.status == node_api.VdrEntryStatus.DEACTIVATED)
      _ <- logResponse(
        "head",
        s"status=${entry.status} hash=${HexString.fromByteArray(entry.eventHash.toByteArray)}"
      )
    } yield entry

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrOperationResult] =
    for {
      _ <- logRequest("create", s"bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}")
      signed <- signer.signCreate(data, didKeyId)
      resp <- ZIO
        .attemptBlocking(stub.createVdrEntry(node_api.CreateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => DriverNotFound(e)
          case e: Throwable              => DriverNotFound(e)
        }
      _ <- logResponse(
        "create",
        s"operationId=${resp.getOutput.getOperationId.toByteArray.length} bytes, output=${resp.getOutput}"
      )
    } yield mapCreateOutput(resp.getOutput, options)

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrOperationResult]] =
    for {
      entryIdHex <- ZIO.succeed(extractHash(url))
      head <- fetchLatestHead(entryIdHex)
      previous = head.eventHash.toByteArray
      _ <- logRequest(
        "update",
        s"entryId=$entryIdHex, head=${HexString.fromByteArray(previous)}, bytes=${data.length}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signUpdate(previous, data, didKeyId)
      resp <- ZIO
        .attemptBlocking(stub.updateVdrEntry(node_api.UpdateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => DriverNotFound(e)
        }
      _ <- logResponse("update", s"output=${resp.getOutput}")
      opIdHex = hexString(resp.getOutput.getOperationId.toByteArray)
    } yield Some(VdrOperationResult(url, Some(opIdHex)))

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    (for {
      hash <- bytesFromHex(extractHash(url))
      _ <- logRequest("read", s"url=$url hashLen=${hash.length}")
      resp <- ZIO
        .attemptBlocking(
          stub.getVdrEntry(
            node_api.GetVdrEntryRequest(
              // prism-node expects the immutable entry hash in eventHash.
              eventHash = com.google.protobuf.ByteString.copyFrom(hash)
            )
          )
        )
        .tapError(e => ZIO.logError(s"[prism-node VDR] read error: ${e}"))
      entry = resp.getEntry
      _ <- ZIO.logDebug(
        s"[prism-node VDR] read entry metadata url=$url status=${entry.status} deactivated=${entry.deactivated} hash=${entry.eventHash.toByteArray.map("%02X" format _).mkString}"
      )
      _ <- ZIO
        .fail(new prism.DataAlreadyDeactivatedException(RefVDR(hexString(hash))))
        .when(entry.deactivated || entry.status == node_api.VdrEntryStatus.DEACTIVATED)
      verification <- ZIO.attemptBlocking(
        stub.verifyVdrEntry(node_api.VerifyVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash)))
      )
      _ <- ZIO
        .fail(new RuntimeException(s"VDR entry verification failed for $url: ${verification.reason}"))
        .when(!verification.valid)
      _ <- logResponse("read", s"entryBytes=${entry.toByteArray.length}, deactivated=${entry.deactivated}")
      data = entry.getData
      dataBytes =
        if data.content.bytes.isDefined then data.getBytes.toByteArray
        else if data.content.ipfs.isDefined then data.getIpfs.getBytes(java.nio.charset.StandardCharsets.UTF_8)
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
      head <- fetchLatestHead(entryIdHex)
      previous = head.eventHash.toByteArray
      _ <- logRequest(
        "delete",
        s"entryId=$entryIdHex, head=${HexString.fromByteArray(previous)}, didKeyId=${didKeyId.getOrElse("none")}"
      )
      signed <- signer.signDeactivate(previous, didKeyId)
      resp <- ZIO
        .attemptBlocking(stub.deactivateVdrEntry(node_api.DeactivateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => DriverNotFound(e)
        }
      _ <- logResponse("delete", s"ok=true")
    } yield Some(hexString(resp.getOutput.getOperationId.toByteArray))

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    (for {
      hash <- bytesFromHex(url)
      _ <- logRequest("verify", s"url=$url, returnData=$returnData")
      resp <- ZIO.attemptBlocking(
        stub.verifyVdrEntry(node_api.VerifyVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash)))
      )
      _ <- logResponse("verify", s"valid=${resp.valid}")
      proofBytes = if (resp.valid) hash else Array.emptyByteArray
    } yield Proof("prism-node", Array.emptyByteArray, proofBytes)).orDie

  override def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus] =
    (for {
      _ <- logRequest("getOperationStatus", s"operationId=$operationId")
      opIdBytes <- ZIO.fromTry(HexString.fromString(operationId)).map(_.toByteArray)
      resp <- ZIO.attemptBlocking(
        stub.getOperationInfo(
          node_api.GetOperationInfoRequest(
            com.google.protobuf.ByteString.copyFrom(opIdBytes)
          )
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
