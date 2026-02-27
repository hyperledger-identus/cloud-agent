package org.hyperledger.identus.iam.authentication.oidc

import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, EntityRole}
import org.hyperledger.identus.iam.authentication.{AuthenticationError, AuthenticatorWithAuthZ, Credentials}
import org.hyperledger.identus.iam.authentication.AuthenticationError.{
  AuthenticationMethodNotEnabled,
  InvalidCredentials
}
import org.hyperledger.identus.shared.utils.Traverse.*
import pdi.jwt.{JwtClaim, JwtOptions, JwtZIOJson}
import zio.*
import zio.json.ast.Json

import java.util.UUID

final class AccessToken private (token: String, claims: JwtClaim, rolesClaimPath: Seq[String]) {

  override def toString(): String = token

  def isRpt: Either[String, Boolean] =
    Json.decoder
      .decodeJson(claims.content)
      .flatMap(_.asObject.toRight("JWT payload must be a JSON object"))
      .map(_.contains("authorization"))

  def role: Either[String, EntityRole] =
    extractRoles
      .map(_.fold(Nil)(_.distinct).toList)
      .flatMap {
        case Nil      => Right(EntityRole.Tenant)
        case r :: Nil => Right(r)
        case _        => Left(s"Multiple roles is not supported yet.")
      }

  /** Return a list of roles that is meaningful to the agent */
  private def extractRoles: Either[String, Option[Seq[EntityRole]]] =
    Json.decoder
      .decodeJson(claims.content)
      .flatMap { json =>
        val rolesJson = rolesClaimPath.foldLeft(Option(json)) { case (acc, pathSegment) =>
          acc.flatMap(_.asObject).flatMap(_.get(pathSegment))
        }
        rolesJson match {
          case None       => Right(None)
          case Some(json) =>
            json.asArray
              .toRight("Roles claim is not a JSON array of strings.")
              .map(_.flatMap(_.asString).flatMap(parseRole))
              .map(Some(_))
        }
      }

  private def parseRole(s: String): Option[EntityRole] = {
    s match {
      case "admin"  => Some(EntityRole.Admin)
      case "tenant" => Some(EntityRole.Tenant)
      case _        => None
    }
  }
}

object AccessToken {
  def fromString(token: String, rolesClaimPath: Seq[String] = Nil): Either[String, AccessToken] =
    JwtZIOJson
      .decode(token, JwtOptions(false, false, false))
      .map(claims => AccessToken(token, claims, rolesClaimPath))
      .toEither
      .left
      .map(e => s"JWT token cannot be decoded. ${e.getMessage()}")
}

final case class KeycloakEntity(id: UUID, accessToken: Option[AccessToken] = None) extends BaseEntity {
  override def role: Either[String, EntityRole] = accessToken
    .toRight("Cannot extract role from KeycloakEntity without accessToken")
    .flatMap(_.role)
}

trait KeycloakAuthenticator extends AuthenticatorWithAuthZ[KeycloakEntity] {
  def authenticate(credentials: Credentials): IO[AuthenticationError, KeycloakEntity] = {
    if (isEnabled) {
      credentials match {
        case JwtCredentials(Some(token)) if token.nonEmpty => authenticate(token)
        case JwtCredentials(Some(_))                       =>
          ZIO.logDebug(s"Keycloak authentication is enabled, but bearer token is empty") *>
            ZIO.fail(JwtAuthenticationError.emptyToken)
        case JwtCredentials(None) =>
          ZIO.logDebug(s"Keycloak authentication is enabled, but bearer token is not provided") *>
            ZIO.fail(InvalidCredentials("Bearer token is not provided"))
        case other =>
          ZIO.fail(InvalidCredentials("Bearer token is not provided"))
      }
    } else ZIO.fail(AuthenticationMethodNotEnabled("Keycloak authentication is not enabled"))
  }

  def authenticate(token: String): IO[AuthenticationError, KeycloakEntity]
}
