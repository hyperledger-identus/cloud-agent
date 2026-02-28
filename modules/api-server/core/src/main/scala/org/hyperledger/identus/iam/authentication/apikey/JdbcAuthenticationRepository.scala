package org.hyperledger.identus.iam.authentication.apikey

import doobie.*
import doobie.implicits.*
import org.hyperledger.identus.shared.db.Errors
import org.hyperledger.identus.shared.db.Implicits.ensureOneAffectedRowOrDie
import org.postgresql.util.PSQLException
import zio.*
import zio.interop.catz.*

import java.time.OffsetDateTime
import java.util.UUID

case class JdbcAuthenticationRepository(xa: Transactor[Task]) extends AuthenticationRepository {

  import AuthenticationRepositoryError.*
  import AuthenticationRepositorySql.*
  override def insert(
      entityId: UUID,
      amt: AuthenticationMethodType,
      secret: String
  ): IO[AuthenticationCompromised, Unit] = {
    val authenticationMethod = AuthenticationMethod(amt, entityId, secret)
    AuthenticationRepositorySql
      .insert(authenticationMethod)
      .transact(xa)
      .flatMap {
        case 1     => ZIO.unit
        case count => ZIO.die(Errors.UnexpectedAffectedRow(count))
      }
      .catchAll {
        case sqlException: PSQLException
            if sqlException.getMessage
              .contains("ERROR: duplicate key value violates unique constraint \"unique_type_secret_constraint\"") =>
          ensureThatTheApiKeyIsNotCompromised(entityId, amt, secret)
        case e => ZIO.die(e)
      }
  }

  private def ensureThatTheApiKeyIsNotCompromised(
      entityId: UUID,
      authenticationMethodType: AuthenticationMethodType,
      secret: String
  ): IO[AuthenticationCompromised, Unit] = {
    val ac = AuthenticationCompromised(entityId, authenticationMethodType, secret)
    val acZIO: IO[AuthenticationCompromised, Unit] = ZIO.fail(ac)

    for {
      authRecordOpt <- findAuthenticationMethodByTypeAndSecret(authenticationMethodType, secret)
      authRecord <- ZIO.fromOption(authRecordOpt).mapError(_ => ac)
      compromisedEntityId = authRecord.entityId
      isTheSameEntityId = authRecord.entityId == entityId
      isNotDeleted = authRecord.deletedAt.isEmpty
      result <-
        if (isTheSameEntityId && isNotDeleted)
          ZIO.unit
        else if (isNotDeleted)
          delete(compromisedEntityId, authenticationMethodType, secret) *> acZIO
        else
          acZIO
    } yield result
  }

  override def findEntityIdByMethodAndSecret(
      amt: AuthenticationMethodType,
      secret: String
  ): UIO[Option[UUID]] = {
    AuthenticationRepositorySql
      .getEntityIdByMethodAndSecret(amt, secret)
      .transact(xa)
      .map(_.headOption)
      .orDie
  }

  override def findAuthenticationMethodByTypeAndSecret(
      amt: AuthenticationMethodType,
      secret: String
  ): UIO[Option[AuthenticationMethod]] = {
    AuthenticationRepositorySql
      .filterByTypeAndSecret(amt, secret)
      .transact(xa)
      .map(_.headOption)
      .orDie
  }

  override def deleteByMethodAndEntityId(
      entityId: UUID,
      amt: AuthenticationMethodType
  ): UIO[Unit] = {
    AuthenticationRepositorySql
      .softDeleteByEntityIdAndType(entityId, amt, Some(OffsetDateTime.now()))
      .transact(xa)
      .map(_ => ())
      .orDie
  }

  override def delete(
      entityId: UUID,
      amt: AuthenticationMethodType,
      secret: String
  ): UIO[Unit] = {
    AuthenticationRepositorySql
      .softDeleteBy(entityId, amt, secret, Some(OffsetDateTime.now()))
      .transact(xa)
      .ensureOneAffectedRowOrDie
  }
}

object JdbcAuthenticationRepository {
  val layer: URLayer[Transactor[Task], AuthenticationRepository] =
    ZLayer.fromFunction(JdbcAuthenticationRepository(_))
}
