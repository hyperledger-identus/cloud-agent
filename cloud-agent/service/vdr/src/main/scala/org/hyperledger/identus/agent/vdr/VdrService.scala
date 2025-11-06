package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import fmgp.crypto.Secp256k1PrivateKey
import fmgp.did.method.prism.{BlockfrostConfig, BlockfrostRyoConfig, DIDPrism}
import fmgp.did.method.prism.cardano.CardanoWalletConfig
import fmgp.did.method.prism.vdr.Indexer
import fmgp.did.method.prism.IndexerConfig
import hyperledger.identus.vdr.prism.PRISMDriver
import interfaces.{Driver, Proof}
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.VdrServiceError.{DriverNotFound, VdrEntryNotFound}
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

  def create(data: Array[Byte], options: VdrOptions): IO[DriverNotFound, VdrUrl]
  def update(data: Array[Byte], url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Option[VdrUrl]]
  def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]]
  def delete(url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Unit]
  def verify(url: VdrUrl, returnData: Boolean = false): UIO[Proof]
}

class VdrServiceImpl(
    proxy: VDRProxyMultiDrivers,
    override val identifier: String,
    override val version: String,
) extends VdrService {

  extension [R, A](z: ZIO[R, Throwable, A]) {
    def refineVdrError: ZIO[R, DriverNotFound | VdrEntryNotFound, A] =
      z.refineOrDie {
        case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
        case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
      }
  }

  override def create(data: Array[Byte], options: VdrOptions): IO[DriverNotFound, VdrUrl] =
    ZIO
      .attemptBlocking(proxy.create(data, options.asJava))
      .refineOrDie { case e: NoDriverWithThisSpecificationsException =>
        DriverNotFound(e)
      }

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions
  ): IO[DriverNotFound | VdrEntryNotFound, Option[VdrUrl]] =
    ZIO
      .attemptBlocking(Option(proxy.update(data, url, options.asJava)))
      .refineVdrError

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    ZIO
      .attemptBlocking(proxy.read(url))
      .refineVdrError

  override def delete(url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Unit] =
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
      prismDriver: Option[PRISMDriverConfig]
  )

  final case class PRISMDriverConfig(
      blockfrostApiKey: String,
      walletMnemonic: Seq[String],
      walletPassphrase: String,
      didPrism: String,
      vdrKeyName: String,
      vdrPrivateKey: Array[Byte],
      prismStateDir: String,
      indexIntervalSecond: Int = 2
  )

  def layer: RLayer[DataSource & Config, VdrService] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[Config]
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
        maybePrismDriver <- initPrismDriver
        drivers = Array[Option[Driver]](maybeMemoryDriver, maybeDatabaseDriver, maybePrismDriver).flatten
        proxy = VDRProxyMultiDrivers(
          urlManager,
          drivers,
          "proxy",
          "0.1.0"
        )
      } yield VdrServiceImpl(proxy, proxy.getIdentifier(), proxy.getVersion())
    }

  private def initPrismDriver: URIO[Config, Option[Driver]] =
    for
      config <- ZIO.service[Config]
      maybePrismDriver = config.prismDriver.map { config =>
        val bfConfig = BlockfrostConfig(
          config.blockfrostApiKey,
          Some(BlockfrostRyoConfig(url = "http://localhost:18082", protocolMagic = 42))
        )
        val driver = PRISMDriver(
          bfConfig = bfConfig,
          wallet = CardanoWalletConfig(config.walletMnemonic, config.walletPassphrase),
          didPrism = DIDPrism(config.didPrism.replace("did:prism:", "")),
          vdrKey = Secp256k1PrivateKey(config.vdrPrivateKey),
          keyName = config.vdrKeyName,
          workdir = config.prismStateDir
        )
        val indexerConfig = IndexerConfig(Some(bfConfig), config.prismStateDir)
        val indexerJob = Indexer.indexerJob
          .tap(_ => driver.initProgram)
          .schedule(Schedule.spaced(config.indexIntervalSecond.seconds))
          .provide(ZLayer.succeed(indexerConfig))
        (driver, indexerJob)
      }
      _ <- config.prismDriver.fold(ZIO.unit) { config => createIndexerDirectories(config.prismStateDir).orDie }
      _ <- maybePrismDriver.fold(ZIO.unit)(_._2).fork
    yield maybePrismDriver.map(_._1)

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
