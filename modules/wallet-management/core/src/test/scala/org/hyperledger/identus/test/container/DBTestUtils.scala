package org.hyperledger.identus.test.container

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.flywaydb.core.api.output.MigrateResult
import org.flywaydb.core.Flyway
import zio.*

object DBTestUtils {
  def runMigrationAgentDB: RIO[PostgreSQLContainer, MigrateResult] = runMigrationPgContainer(
    "public",
    "classpath:sql/agent"
  )
  def runMigrationPgContainer(schema: String, paths: String*): RIO[PostgreSQLContainer, MigrateResult] =
    for {
      pg <- ZIO.service[PostgreSQLContainer]
      result <- runMigration(pg.jdbcUrl, pg.username, pg.password, schema, paths*)
    } yield result

  def runMigration(
      url: String,
      username: String,
      password: String,
      schema: String,
      locations: String*
  ): Task[MigrateResult] =
    ZIO.attemptBlocking {
      Flyway
        .configure()
        .dataSource(url, username, password)
        .schemas(schema)
        .locations(locations*)
        .load()
        .migrate()
    }
}
