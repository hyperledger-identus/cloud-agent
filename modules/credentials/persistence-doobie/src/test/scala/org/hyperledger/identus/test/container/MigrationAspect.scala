package org.hyperledger.identus.test.container

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.flywaydb.core.Flyway
import zio.test.{TestAspect, TestAspectAtLeastR}
import zio.test.TestAspect.{before, beforeAll}
import zio.ZIO

object MigrationAspects {
  def migrate(schema: String, paths: String*): TestAspectAtLeastR[PostgreSQLContainer] = {
    val migration = for {
      pg <- ZIO.service[PostgreSQLContainer]
      _ <- runMigration(pg.jdbcUrl, pg.username, pg.password, schema, paths*)
    } yield ()

    beforeAll(migration.orDie)
  }

  def migrateEach(schema: String, paths: String*): TestAspectAtLeastR[PostgreSQLContainer] = {
    val migration = for {
      pg <- ZIO.service[PostgreSQLContainer]
      _ <- runMigration(pg.jdbcUrl, pg.username, pg.password, schema, paths*)
    } yield ()

    before(migration.orDie)
  }

  def runMigration(
      url: String,
      username: String,
      password: String,
      schema: String,
      locations: String*
  ) =
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
