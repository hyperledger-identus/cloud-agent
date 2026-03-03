package org.hyperledger.identus.iam.authentication.apikey

import io.getquill.*
import io.getquill.context.json.PostgresJsonExtensions
import io.getquill.doobie.DoobieContext
import org.hyperledger.identus.iam.authentication.apikey.AuthenticationRepositoryError.AuthenticationCompromised
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import zio.interop.catz.*

import java.time.OffsetDateTime
import java.util.UUID

enum AuthenticationMethodType(val value: String) {
  case ApiKey extends AuthenticationMethodType("apikey")
}

object AuthenticationMethodType {
  def fromString(value: String) = {
    AuthenticationMethodType.values.find(_.value == value).get
  }
}

case class AuthenticationMethod(
    `type`: AuthenticationMethodType,
    entityId: UUID,
    secret: String,
    createdAt: OffsetDateTime = OffsetDateTime.now(),
    deletedAt: Option[OffsetDateTime] = None
) {
  def isDeleted = deletedAt.isDefined
}

trait AuthenticationRepository {
  def insert(
      entityId: UUID,
      amt: AuthenticationMethodType,
      secret: String
  ): zio.IO[AuthenticationCompromised, Unit]

  def findEntityIdByMethodAndSecret(
      amt: AuthenticationMethodType,
      secret: String
  ): zio.UIO[Option[UUID]]

  def findAuthenticationMethodByTypeAndSecret(
      amt: AuthenticationMethodType,
      secret: String
  ): zio.UIO[Option[AuthenticationMethod]]

  def deleteByMethodAndEntityId(
      entityId: UUID,
      amt: AuthenticationMethodType
  ): zio.UIO[Unit]

  def delete(
      entityId: UUID,
      amt: AuthenticationMethodType,
      secret: String
  ): zio.UIO[Unit]
}

//TODO: reconsider the hierarchy of the service and dal layers
sealed trait AuthenticationRepositoryError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "AuthenticationRepositoryError"
}

object AuthenticationRepositoryError {

  private def hide(secret: String) = secret.take(8) + "****"

  case class AuthenticationCompromised(
      entityId: UUID,
      authenticationMethodType: AuthenticationMethodType,
      secret: String
  ) extends AuthenticationRepositoryError(
        StatusCode.Unauthorized,
        s"Authentication method is compromised for entityId:$entityId, type:${authenticationMethodType.value}, and secret:${hide(secret)}"
      )
}

object AuthenticationRepositorySql extends DoobieContext.Postgres(SnakeCase) with PostgresJsonExtensions {

  implicit val authenticationMethodType2String: MappedEncoding[AuthenticationMethodType, String] =
    MappedEncoding[AuthenticationMethodType, String](_.value)

  implicit val string2AuthenticationMethodType: MappedEncoding[String, AuthenticationMethodType] =
    MappedEncoding[String, AuthenticationMethodType](AuthenticationMethodType.fromString)

  def insert(authenticationMethod: AuthenticationMethod) = {
    run {
      quote {
        query[AuthenticationMethod].insertValue(lift(authenticationMethod))
      }
    }
  }

  def getEntityIdByMethodAndSecret(amt: AuthenticationMethodType, secret: String) = {
    run {
      quote {
        query[AuthenticationMethod]
          .filter(am => am.secret == lift(secret) && am.`type` == lift(amt) && am.deletedAt.isEmpty)
          .map(_.entityId)
          .take(1)
      }
    }
  }

  def filterByTypeAndSecret(amt: AuthenticationMethodType, secret: String) = {
    run {
      quote {
        query[AuthenticationMethod]
          .filter(am => am.secret == lift(secret) && am.`type` == lift(amt))
      }
    }
  }

  def softDeleteByEntityIdAndType(
      entityId: UUID,
      amt: AuthenticationMethodType,
      deletedAt: Option[OffsetDateTime]
  ) = {
    run {
      quote {
        query[AuthenticationMethod]
          .filter(am => am.`type` == lift(amt) && am.entityId == lift(entityId))
          .update(_.deletedAt -> lift(deletedAt))
      }
    }
  }

  def softDeleteBy(
      entityId: UUID,
      amt: AuthenticationMethodType,
      secret: String,
      deletedAt: Option[OffsetDateTime]
  ) = {
    run {
      quote {
        query[AuthenticationMethod]
          .filter(am => am.entityId == lift(entityId) && am.`type` == lift(amt) && am.secret == lift(secret))
          .update(_.deletedAt -> lift(deletedAt))
      }
    }
  }
}
