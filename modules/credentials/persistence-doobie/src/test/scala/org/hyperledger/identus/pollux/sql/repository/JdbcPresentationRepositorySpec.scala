package org.hyperledger.identus.pollux.sql.repository

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.hyperledger.identus.pollux.core.repository.*
import org.hyperledger.identus.shared.db.DbConfig
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import zio.*
import zio.test.*

object JdbcPresentationRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val dbConfig = ZLayer.fromZIO(
    for {
      postgres <- ZIO.service[PostgreSQLContainer]
    } yield DbConfig(postgres.username, postgres.password, postgres.jdbcUrl)
  )

  private val testEnvironmentLayer = ZLayer.make[PresentationRepository & Migrations](
    JdbcPresentationRepository.layer,
    Migrations.layer,
    dbConfig,
    pgContainerLayer,
    contextAwareTransactorLayer,
    systemTransactorLayer
  )

  override def spec =
    (suite("JDBC Presentation Repository test suite")(
      PresentationRepositorySpecSuite.testSuite,
      PresentationRepositorySpecSuite.multitenantTestSuite
    ) @@ TestAspect.before(
      ZIO.serviceWithZIO[Migrations](_.migrate)
    )).provide(testEnvironmentLayer)
      .provide(Runtime.removeDefaultLoggers)

}
