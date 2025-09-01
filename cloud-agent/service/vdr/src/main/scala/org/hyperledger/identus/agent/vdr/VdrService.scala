package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import fmgp.crypto.Secp256k1PrivateKey
import fmgp.did.method.prism.{BlockfrostConfig, DIDPrism}
import fmgp.did.method.prism.cardano.CardanoWalletConfig
import hyperledger.identus.vdr.prism.PRISMDriver
import interfaces.{Driver, Proof}
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.VdrServiceError.{DriverNotFound, VdrEntryNotFound}
import proxy.VDRProxyMultiDrivers
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException
import urlManagers.BaseUrlManager
import zio.*

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
      vdrKey: String,
      vdrKeyName: String,
      vdrPrivateKey: Array[Byte],
      prismStateDir: String
  )

  def layer: RLayer[DataSource & Config, VdrService] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[Config]
        urlManager <- ZIO.attempt(BaseUrlManager.apply("vdr://", "BaseURL"))
        dbDriverDataSource <- ZIO.service[DataSource]
        // TODO: make each driver optional and configurable
        maybePrismDriver = config.prismDriver.map { config =>
          PRISMDriver(
            bfConfig = BlockfrostConfig(config.blockfrostApiKey),
            wallet = CardanoWalletConfig(config.walletMnemonic, config.walletPassphrase),
            didPrism = DIDPrism(config.didPrism),
            vdrKey = Secp256k1PrivateKey(config.vdrPrivateKey),
            keyName = config.vdrKeyName,
            workdir = config.prismStateDir
          )
        }
        drivers = Array[Driver](
          InMemoryDriver("memory", "memory", "0.1.0", Array.empty),
          DatabaseDriver("database", "0.1.0", Array.empty, dbDriverDataSource)
        ) ++ maybePrismDriver.toSeq
        proxy = VDRProxyMultiDrivers(
          urlManager,
          drivers,
          "proxy",
          "0.1.0"
        )
      } yield VdrServiceImpl(proxy, proxy.getIdentifier(), proxy.getVersion())
    }
}
