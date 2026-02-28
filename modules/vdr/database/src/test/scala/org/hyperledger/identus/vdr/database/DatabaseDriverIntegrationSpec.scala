package org.hyperledger.identus.vdr.database

import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer
import zio.*
import zio.test.*

import java.nio.charset.StandardCharsets
import java.sql.{Connection, DriverManager}

object DatabaseDriverIntegrationSpec extends ZIOSpecDefault:

  private def withPostgres[R](body: PostgreSQLContainer[?] => Task[R]): ZIO[Scope, Throwable, R] =
    ZIO
      .acquireRelease(ZIO.attempt {
        val container = new PostgreSQLContainer("postgres:15-alpine")
        container.start()
        container
      })(c => ZIO.attempt(c.stop()).ignore)
      .flatMap(body)

  private def dataSource(pg: PostgreSQLContainer[?]): DataSource = new DataSource:
    Class.forName("org.postgresql.Driver")
    override def getConnection(): Connection =
      DriverManager.getConnection(pg.getJdbcUrl, pg.getUsername, pg.getPassword)
    override def getConnection(username: String, password: String): Connection =
      DriverManager.getConnection(pg.getJdbcUrl, username, password)
    override def getLogWriter: java.io.PrintWriter = null
    override def setLogWriter(out: java.io.PrintWriter): Unit = ()
    override def setLoginTimeout(seconds: Int): Unit = ()
    override def getLoginTimeout: Int = 0
    override def getParentLogger: java.util.logging.Logger = java.util.logging.Logger.getGlobal
    override def unwrap[T](iface: Class[T]): T = throw new UnsupportedOperationException
    override def isWrapperFor(iface: Class[?]): Boolean = false

  override def spec: Spec[Any, Any] = suite("DatabaseDriver integration")(
    test("create and read round-trip against Postgres") {
      ZIO.scoped {
        withPostgres { pg =>
          val ds = dataSource(pg)
          for {
            driver <- ZIO
              .fromOption(DatabaseDriverProvider.load(enabled = true, ds))
              .orElseFail(new RuntimeException("driver not loaded"))
            data = "hello-db".getBytes(StandardCharsets.UTF_8)
            op = driver.create(data, java.util.Map.of())
            fragment = op.getFragment
            fetched = driver.read(Array.empty, java.util.Map.of(), fragment, Array.empty)
          } yield assertTrue(fetched.sameElements(data))
        }
      }
    }
  )
