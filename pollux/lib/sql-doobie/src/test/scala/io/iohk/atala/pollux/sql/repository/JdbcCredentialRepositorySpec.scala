package io.iohk.atala.pollux.sql.repository

import com.dimafeng.testcontainers.PostgreSQLContainer
import io.iohk.atala.pollux.core.repository._
import io.iohk.atala.shared.db.DbConfig
import io.iohk.atala.shared.test.containers.PostgresTestContainerSupport
import zio._
import zio.test._

object JdbcCredentialRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val dbConfig = ZLayer.fromZIO(
    for {
      postgres <- ZIO.service[PostgreSQLContainer]
    } yield DbConfig(postgres.username, postgres.password, postgres.jdbcUrl)
  )

  private val testEnvironmentLayer = ZLayer.make[CredentialRepository & Migrations](
    JdbcCredentialRepository.layer,
    Migrations.layer,
    dbConfig,
    pgContainerLayer,
    contextAwareTransactorLayer
  )

  override def spec =
    (suite("JDBC Credential Repository test suite")(
      CredentialRepositorySpecSuite.testSuite,
      CredentialRepositorySpecSuite.multitenantTestSuite
    ) @@ TestAspect.before(
      ZIO.serviceWithZIO[Migrations](_.migrate)
    )).provide(testEnvironmentLayer)
      .provide(Runtime.removeDefaultLoggers)

}
