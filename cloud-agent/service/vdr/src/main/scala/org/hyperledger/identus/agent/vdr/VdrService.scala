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
  PrismStateInMemory
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

trait VdrService {
  def identifier: String
  def version: String

  def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrUrl]
  def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrUrl]]
  def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]]
  def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Unit]
  def verify(url: VdrUrl, returnData: Boolean = false): UIO[Proof]
}

class VdrServiceImpl(
    proxy: VDRProxyMultiDrivers,
    override val identifier: String,
    override val version: String,
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

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound, VdrUrl] =
    ZIO
      .attemptBlocking(proxy.create(data, options.asJava))
      .refineOrDie { case e: NoDriverWithThisSpecificationsException =>
        DriverNotFound(e)
      }

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound, Option[VdrUrl]] =
    ZIO
      .attemptBlocking(Option(proxy.update(data, url, options.asJava)))
      .refineVdrError

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    ZIO
      .attemptBlocking(proxy.read(url))
      .refineVdrError

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound, Unit] =
    ZIO
      .attemptBlocking(proxy.delete(url, options.asJava))
      .refineVdrError

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    ZIO.attemptBlocking(proxy.verify(url, returnData)).orDie

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
        service <-
          config.prismNodeDriver match {
            case Some(_) =>
              for {
                signer <- ZIO.service[VdrOperationSigner]
                stub <- ZIO.service[node_api.NodeServiceGrpc.NodeServiceBlockingStub]
                svc <- PrismNodeVdrService.init(stub, signer)
              } yield svc
            case None =>
              for {
                urlManager <- ZIO.attempt(BaseUrlManager.apply("vdr://", "BaseURL"))
                dbDriverDataSource <- ZIO.service[DataSource]
                maybeMemoryDriver =
                  if config.enableInMemoryDriver
                  then Some(InMemoryDriver("memory", "memory", "0.1.0", Array.empty))
                  else None
                maybeDatabaseDriver =
                  if config.enableDatabaseDriver
                  then Some(DatabaseDriver("database", "0.1.0", Array.empty, dbDriverDataSource))
                  else None
                maybePrismDriver <- config.prismDriver.fold(ZIO.none)(config => initPrismDriver(config).asSome)
                drivers = Array[Option[Driver]](maybeMemoryDriver, maybeDatabaseDriver, maybePrismDriver).flatten
                proxy = VDRProxyMultiDrivers(
                  urlManager,
                  drivers,
                  "proxy",
                  "0.1.0"
                )
              } yield VdrServiceImpl(proxy, proxy.getIdentifier(), proxy.getVersion())
          }
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
  def init(stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub, signer: VdrOperationSigner): Task[VdrService] =
    ZIO.succeed(new PrismNodeVdrService(stub, signer))
}

class PrismNodeVdrService(
    stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub,
    signer: VdrOperationSigner
) extends VdrService {
  override val identifier: String = "prism-node"
  override val version: String = "0.1.0"

  private def hexString(bytes: Array[Byte]): String = HexString.fromByteArray(bytes).toString
  private def bytesFromHex(url: String): Task[Array[Byte]] = ZIO.fromTry(HexString.fromString(url)).map(_.toByteArray)

  private def mapCreateOutput(out: node_api.OperationOutput): String =
    out.result match {
      case node_api.OperationOutput.Result.CreateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case node_api.OperationOutput.Result.UpdateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case node_api.OperationOutput.Result.DeactivateVdrEntryOutput(vdrOut) =>
        hexString(vdrOut.eventHash.toByteArray)
      case _ => hexString(out.getOperationId.toByteArray)
    }

  private def mapStatusError(e: StatusRuntimeException): DriverNotFound | VdrEntryNotFound =
    if (e.getStatus.getCode == Status.Code.NOT_FOUND) VdrEntryNotFound(e) else DriverNotFound(e)

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey, VdrUrl] =
    for {
      signed <- signer.signCreate(data, didKeyId)
      resp <- ZIO
        .attemptBlocking(stub.createVdrEntry(node_api.CreateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => DriverNotFound(e)
          case e: Throwable              => DriverNotFound(e)
        }
    } yield mapCreateOutput(resp.getOutput)

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Option[VdrUrl]] =
    for {
      previous <- bytesFromHex(url).mapError(DriverNotFound(_))
      signed <- signer.signUpdate(previous, data, didKeyId)
      resp <- ZIO
        .attemptBlocking(stub.updateVdrEntry(node_api.UpdateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => DriverNotFound(e)
        }
    } yield Some(mapCreateOutput(resp.getOutput))

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    (for {
      hash <- bytesFromHex(url)
      resp <- ZIO.attemptBlocking(
        stub.getVdrEntry(node_api.GetVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash)))
      )
    } yield resp.getEntry.toByteArray).mapError {
      case e: StatusRuntimeException => mapStatusError(e)
      case e: Throwable              => DriverNotFound(e)
    }

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey, Unit] =
    for {
      previous <- bytesFromHex(url).mapError(DriverNotFound(_))
      signed <- signer.signDeactivate(previous, didKeyId)
      _ <- ZIO
        .attemptBlocking(stub.deactivateVdrEntry(node_api.DeactivateVdrEntryRequest(Some(signed))))
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => DriverNotFound(e)
        }
    } yield ()

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    (for {
      hash <- bytesFromHex(url)
      resp <- ZIO.attemptBlocking(
        stub.verifyVdrEntry(node_api.VerifyVdrEntryRequest(com.google.protobuf.ByteString.copyFrom(hash)))
      )
      proofBytes = if (resp.valid) hash else Array.emptyByteArray
    } yield Proof("prism-node", Array.emptyByteArray, proofBytes)).orDie
}
