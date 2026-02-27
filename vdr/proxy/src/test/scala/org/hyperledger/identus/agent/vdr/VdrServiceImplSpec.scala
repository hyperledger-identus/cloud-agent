package org.hyperledger.identus.agent.vdr

import interfaces.{Driver, Proof}
import io.grpc.ManagedChannelBuilder
import io.iohk.atala.prism.protos.node_api
import javax.sql.DataSource
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import proxy.VDRProxyMultiDrivers
import urlManagers.BaseUrlManager
import zio.*
import zio.test.*

import java.security.PublicKey
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

object VdrServiceImplSpec extends ZIOSpecDefault {
  private val VdrScheme = "vdr://"

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
        val proxy = VDRProxyMultiDrivers(BaseUrlManager(VdrScheme, "BaseURL"), Array(driver), "proxy", "0.1.0")
        val svc = new VdrServiceImpl(
          Some(proxy),
          None,
          "proxy",
          "0.1.0"
        )

        for {
          _ <- svc.read(s"${VdrScheme}foo")
          res <- svc.create("data".getBytes(), Map.empty, None).provideLayer(walletLayer)
        } yield assertTrue(
          driver.readCount.get() == 1,
          driver.createCount.get() == 1,
          res.url.startsWith(VdrScheme)
        )
      },
      test("routes raw URLs to prism-node service when present") {
        val driver = new CountingDriver("memory")
        val proxy = VDRProxyMultiDrivers(BaseUrlManager(VdrScheme, "BaseURL"), Array(driver), "proxy", "0.1.0")
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
        val proxy = VDRProxyMultiDrivers(BaseUrlManager(VdrScheme, "BaseURL"), Array(driver), "proxy", "0.1.0")
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
      },
      test("selects matching proxy driver family when specified") {
        val first = new CountingDriver("first")
        val second = new CountingDriver("second")
        val proxy =
          VDRProxyMultiDrivers(BaseUrlManager(VdrScheme, "BaseURL"), Array(first, second), "proxy", "0.1.0")
        val svc = new VdrServiceImpl(
          Some(proxy),
          None,
          "proxy",
          "0.1.0"
        )

        for {
          _ <- svc.create("data".getBytes(), Map("drf" -> "first"), None).provideLayer(walletLayer)
        } yield assertTrue(first.createCount.get() == 1, second.createCount.get() == 0)
      },
      test("layer wiring prefers memory driver when both memory and db enabled") {
        val ds = new org.h2.jdbcx.JdbcDataSource()
        ds.setURL("jdbc:h2:mem:vdr-layer-proxy;DB_CLOSE_DELAY=-1")
        ds.setUser("sa"); ds.setPassword("")

        val config = VdrServiceImpl.Config(
          enableInMemoryDriver = true,
          enableDatabaseDriver = true,
          prismDriver = None,
          prismNodeDriver = None
        )

        val signer = new VdrOperationSigner:
          private val err = VdrServiceError.MissingVdrKey(new RuntimeException("unused"))
          override def signCreate(data: Array[Byte], didKeyId: Option[String]) =
            ZIO.fail(err)
          override def signUpdate(previousEventHash: Array[Byte], data: Array[Byte], didKeyId: Option[String]) =
            ZIO.fail(err)
          override def signDeactivate(previousEventHash: Array[Byte], didKeyId: Option[String]) =
            ZIO.fail(err)

        val channel = ManagedChannelBuilder.forTarget("noop").usePlaintext().build()
        val stub = node_api.NodeServiceGrpc.blockingStub(channel)

        val layer =
          ZLayer.succeed(config) ++
            ZLayer.succeed(ds: DataSource) ++
            ZLayer.succeed(signer) ++
            ZLayer.succeed(stub) >>>
            VdrServiceImpl.layer

        val ctx = WalletAccessContext(WalletId.random)
        val program =
          for {
            svc <- ZIO.service[VdrService].provideLayer(layer)
            _ <- svc.create("data".getBytes(), Map("drf" -> "memory"), None).provideSomeLayer(ZLayer.succeed(ctx))
            // query DB to ensure no row was inserted by the DB driver
            count <- ZIO.attempt {
              val conn = ds.getConnection()
              try {
                val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM storage")
                rs.next()
                rs.getInt(1)
              } finally conn.close()
            }
          } yield assertTrue(
            count == 0 // DB driver did not handle the create
          )

        program
      }
    )
}
