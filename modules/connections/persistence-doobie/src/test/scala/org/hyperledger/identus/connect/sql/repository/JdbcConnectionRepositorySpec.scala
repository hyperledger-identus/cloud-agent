package org.hyperledger.identus.connect.sql.repository

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.hyperledger.identus.connect.core.repository.{ConnectionRepository, ConnectionRepositorySpecSuite}
import org.hyperledger.identus.shared.db.DbConfig
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import zio.*
import zio.test.*

object JdbcConnectionRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val dbConfig = ZLayer.fromZIO(
    for {
      postgres <- ZIO.service[PostgreSQLContainer]
    } yield DbConfig(postgres.username, postgres.password, postgres.jdbcUrl)
  )

  private val testEnvironmentLayer = ZLayer.make[ConnectionRepository & Migrations](
    JdbcConnectionRepository.layer,
    Migrations.layer,
    dbConfig,
    pgContainerLayer,
    contextAwareTransactorLayer,
    systemTransactorLayer
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    (suite("JDBC Connection Repository test suite")(
      ConnectionRepositorySpecSuite.testSuite,
      ConnectionRepositorySpecSuite.multitenantTestSuite
    ) @@ TestAspect.sequential @@ TestAspect.before(
      ZIO.serviceWithZIO[Migrations](_.migrate)
    )).provide(testEnvironmentLayer)
      .provide(Runtime.removeDefaultLoggers)
}
