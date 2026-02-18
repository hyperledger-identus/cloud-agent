package org.hyperledger.identus.agent.vdr

import interfaces.{Driver, Proof}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import proxy.VDRProxyMultiDrivers
import urlManagers.BaseUrlManager
import zio.*
import zio.test.*

import java.security.PublicKey
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

object VdrServiceImplSpec extends ZIOSpecDefault {

  private class CountingDriver(id: String) extends Driver {
    val createCount = new AtomicInteger(0)
    val readCount = new AtomicInteger(0)

    override def getIdentifier(): String = id
    override def getFamily(): String = id
    override def getVersion(): String = "0.1.0"
    override def getSupportedVersions(): Array[String] = Array("0.1.0")

    override def create(data: Array[Byte], options: java.util.Map[String, ?]): Driver.OperationResult = {
      createCount.incrementAndGet()
      Driver.OperationResult(
        id,
        Driver.OperationState.SUCCESS,
        Array("path"),
        Map.empty[String, String].asJava,
        "frag",
        Array.empty[PublicKey],
        null
      )
    }

    override def update(
        data: Array[Byte],
        paths: Array[String],
        queries: java.util.Map[String, String],
        fragment: String,
        options: java.util.Map[String, ?]
    ): Driver.OperationResult =
      throw new UnsupportedOperationException

    override def read(
        paths: Array[String],
        queries: java.util.Map[String, String],
        fragment: String,
        publicKeys: Array[PublicKey]
    ): Array[Byte] = {
      readCount.incrementAndGet()
      "proxy".getBytes()
    }

    override def delete(
        paths: Array[String],
        queries: java.util.Map[String, String],
        fragment: String,
        options: java.util.Map[String, ?]
    ): Unit = throw new UnsupportedOperationException

    override def verify(
        paths: Array[String],
        queries: java.util.Map[String, String],
        fragment: String,
        publicKeys: Array[PublicKey],
        returnData: Boolean
    ): Proof = Proof("type", Array.emptyByteArray, Array.emptyByteArray)

    override def storeResultState(identifier: String): Driver.OperationState = Driver.OperationState.SUCCESS
  }

  private class CountingPrismService extends VdrService {
    val readCount = new AtomicInteger(0)
    val createCount = new AtomicInteger(0)
    override val identifier: String = "prism-node"
    override val version: String = "0.1.0"

    override def create(
        data: Array[Byte],
        options: VdrOptions,
        didKeyId: Option[String]
    ): ZIO[WalletAccessContext, VdrServiceError.DriverNotFound | VdrServiceError.MissingVdrKey, VdrOperationResult] =
      ZIO.succeed {
        createCount.incrementAndGet()
        VdrOperationResult("vdr://prism#hash", Some("op-1"))
      }

    override def update(
        data: Array[Byte],
        url: VdrUrl,
        options: VdrOptions,
        didKeyId: Option[String]
    ): ZIO[
      WalletAccessContext,
      VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey,
      Option[VdrOperationResult]
    ] =
      ZIO.fail(VdrServiceError.DriverNotFound(new RuntimeException("unused")))

    override def read(url: VdrUrl): IO[VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound, Array[Byte]] =
      ZIO.succeed {
        readCount.incrementAndGet()
        "prism".getBytes()
      }

    override def delete(
        url: VdrUrl,
        options: VdrOptions,
        didKeyId: Option[String]
    ): ZIO[
      WalletAccessContext,
      VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey,
      Option[String]
    ] =
      ZIO.succeed(Some("op-1"))

    override def verify(url: VdrUrl, returnData: Boolean): UIO[interfaces.Proof] =
      ZIO.succeed(Proof("prism", Array.emptyByteArray, Array.emptyByteArray))

    override def getOperationStatus(operationId: String): IO[VdrServiceError.DriverNotFound, VdrOperationStatus] =
      ZIO.succeed(VdrOperationStatus("CONFIRMED_AND_APPLIED", None, None))
  }

  private val walletLayer = ZLayer.succeed(WalletAccessContext(WalletId.random))

  override def spec: Spec[TestEnvironment, Any] =
    suite("VdrServiceImpl routing")(
      test("routes proxy URLs to proxy driver") {
        val driver = new CountingDriver("memory")
        val proxy = VDRProxyMultiDrivers(BaseUrlManager("vdr://", "BaseURL"), Array(driver), "proxy", "0.1.0")
        val svc = new VdrServiceImpl(
          Some(proxy),
          None,
          "proxy",
          "0.1.0"
        )

        for {
          _ <- svc.read("vdr://foo")
          res <- svc.create("data".getBytes(), Map.empty, None).provideLayer(walletLayer)
        } yield assertTrue(
          driver.readCount.get() == 1,
          driver.createCount.get() == 1,
          res.url.startsWith("vdr://")
        )
      },
      test("routes raw URLs to prism-node service when present") {
        val driver = new CountingDriver("memory")
        val proxy = VDRProxyMultiDrivers(BaseUrlManager("vdr://", "BaseURL"), Array(driver), "proxy", "0.1.0")
        val prism = new CountingPrismService
        val svc = new VdrServiceImpl(
          Some(proxy),
          Some(prism),
          "proxy",
          "0.1.0"
        )

        for {
          _ <- svc.read("deadbeef").provideLayer(walletLayer)
        } yield assertTrue(prism.readCount.get() == 1, driver.readCount.get() == 0)
      },
      test("create uses prism-node when drid=prism-node") {
        val driver = new CountingDriver("memory")
        val proxy = VDRProxyMultiDrivers(BaseUrlManager("vdr://", "BaseURL"), Array(driver), "proxy", "0.1.0")
        val prism = new CountingPrismService
        val svc = new VdrServiceImpl(
          Some(proxy),
          Some(prism),
          "proxy",
          "0.1.0"
        )

        for {
          _ <- svc.create("data".getBytes(), Map("drid" -> "prism-node"), None).provideLayer(walletLayer)
        } yield assertTrue(prism.createCount.get() == 1, driver.createCount.get() == 0)
      }
    )
}
