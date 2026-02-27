package org.hyperledger.identus.agent.server.sql

import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.flywaydb.core.api.exception.FlywayValidateException
import org.flywaydb.core.Flyway
import org.hyperledger.identus.shared.db.{ContextAwareTask, DbConfig}
import org.hyperledger.identus.shared.db.Implicits.*
import zio.*
import zio.interop.catz.*

final case class Migrations(config: DbConfig) {

  val migrationScriptsLocation: String = "sql/agent"

  def migrate: Task[Unit] =
    for {
      _ <- ZIO.logInfo("Applying database migrations")
      _ <- ZIO.attempt {
        Flyway
          .configure()
          .dataSource(
            config.jdbcUrl,
            config.username,
            config.password
          )
          .locations(migrationScriptsLocation)
          .load()
          .migrate()
      }
    } yield ()

  def repair: Task[Unit] =
    for {
      _ <- ZIO.logInfo("Repairing Flyway schema history")
      _ <- ZIO.attempt {
        Flyway
          .configure()
          .dataSource(config.jdbcUrl, config.username, config.password)
          .locations(migrationScriptsLocation)
          .load()
          .repair()
      }
    } yield ()

  def migrateAndRepair: Task[Unit] =
    migrate.catchSome { case e: FlywayValidateException =>
      ZIO.logError("Migration validation failed, attempting to repair") *> repair *> migrate
    }

}

object Migrations {
  val layer: URLayer[DbConfig, Migrations] =
    ZLayer.fromFunction(Migrations.apply)

  /** Fail if the RLS is not enabled from a sample table */
  def validateRLS: RIO[Transactor[ContextAwareTask], Unit] = {
    val cxnIO = sql"""
      | SELECT row_security_active('public.peer_did');
      """.stripMargin
      .query[Boolean]
      .unique

    for {
      xa <- ZIO.service[Transactor[ContextAwareTask]]
      isRlsActive <- cxnIO.transactWithoutContext(xa)
      _ <- ZIO
        .fail(Exception("The RLS policy is not active for Agent DB application user"))
        .unless(isRlsActive)
    } yield ()
  }

  def initDbPrivileges(appUser: String): RIO[Transactor[Task], Unit] = {
    val cxnIO = for {
      _ <- doobie.free.connection.createStatement.map { stm =>
        stm.execute(s"""
          | ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "$appUser"
          """.stripMargin)
      }
    } yield ()

    for {
      xa <- ZIO.service[Transactor[Task]]
      _ <- cxnIO.transact(xa)
    } yield ()
  }
}
