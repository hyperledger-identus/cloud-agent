package org.hyperledger.identus.iam.authentication

import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, Entity, EntityRole}
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.iam.authentication.admin.AdminApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.AuthenticationError.AuthenticationMethodNotEnabled
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext}
import zio.*

import scala.language.implicitConversions

object SecurityLogic {

  def authenticate[E <: BaseEntity](credentials: Credentials, others: Credentials*)(
      authenticator: Authenticator[E],
  ): IO[ErrorResponse, Either[Entity, E]] = {
    val creds = credentials :: others.toList
    ZIO
      .validateFirst(creds)(authenticator.authenticate)
      .map(Right(_))
      .catchAll { errors =>
        val errorsExcludingMethodNotEnabled = errors.filter {
          case AuthenticationMethodNotEnabled(_) => false
          case _                                 => true
        }

        // if the alternative authentication method is not configured,
        // apikey authentication is disabled the default user is used
        errorsExcludingMethodNotEnabled match {
          case Nil       => ZIO.left(Entity.Default)
          case head :: _ => ZIO.fail(head)
        }
      }
  }

  def authorizeWalletAccess[E <: BaseEntity](
      entity: E
  )(authorizer: Authorizer[E]): IO[ErrorResponse, WalletAccessContext] =
    authorizer
      .authorizeWalletAccess(entity)

  def authorizeWalletAccess[E <: BaseEntity](credentials: Credentials, others: Credentials*)(
      authenticator: Authenticator[E],
      authorizer: Authorizer[E]
  ): IO[ErrorResponse, WalletAccessContext] =
    authenticate[E](credentials, others*)(authenticator)
      .flatMap {
        case Left(entity)  => authorizeWalletAccess(entity)(EntityAuthorizer)
        case Right(entity) => authorizeWalletAccess(entity)(authorizer)
      }

  def authorizeWalletAccessWith[E <: BaseEntity](credentials: (ApiKeyCredentials, JwtCredentials))(
      authenticator: Authenticator[E],
      authorizer: Authorizer[E]
  ): IO[ErrorResponse, WalletAccessContext] =
    authorizeWalletAccess[E](credentials._2, credentials._1)(authenticator, authorizer)

  def authorizeWalletAdmin[E <: BaseEntity](
      entity: E
  )(authorizer: Authorizer[E]): IO[ErrorResponse, WalletAdministrationContext] =
    authorizer
      .authorizeWalletAdmin(entity)

  def authorizeWalletAdminWith[E <: BaseEntity](
      credentials: (AdminApiKeyCredentials, ApiKeyCredentials, JwtCredentials)
  )(
      authenticator: Authenticator[E],
      authorizer: Authorizer[E]
  ): IO[ErrorResponse, (BaseEntity, WalletAdministrationContext)] =
    authenticate[E](credentials._1, credentials._3, credentials._2)(authenticator)
      .flatMap {
        case Left(entity)  => authorizeWalletAdmin(entity)(EntityAuthorizer).map(entity -> _)
        case Right(entity) => authorizeWalletAdmin(entity)(authorizer).map(entity -> _)
      }

  def authorizeRole[E <: BaseEntity](credentials: Credentials, others: Credentials*)(
      authenticator: Authenticator[E],
  )(permittedRole: EntityRole): IO[ErrorResponse, BaseEntity] = {
    authenticate[E](credentials, others*)(authenticator)
      .flatMap { ee =>
        val entity = ee.fold(identity, identity)
        for {
          role <-
            ZIO
              .fromEither(entity.role)
              .mapError(msg =>
                AuthenticationError.UnexpectedError(s"Unable to retrieve entity role for entity id ${entity.id}. $msg")
              )
          _ <- ZIO
            .fail(AuthenticationError.InvalidRole(s"$role role is not permitted. Expected $permittedRole role."))
            .when(role != permittedRole)
        } yield entity
      }
  }

  def authorizeRoleWith[E <: BaseEntity](credentials: (AdminApiKeyCredentials, JwtCredentials))(
      authenticator: Authenticator[E],
  )(permittedRole: EntityRole): IO[ErrorResponse, BaseEntity] =
    authorizeRole(credentials._1, credentials._2)(authenticator)(permittedRole)

}
