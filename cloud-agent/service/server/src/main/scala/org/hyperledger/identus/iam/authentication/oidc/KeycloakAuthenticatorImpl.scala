package org.hyperledger.identus.iam.authentication.oidc

import org.hyperledger.identus.agent.walletapi.model.EntityRole
import org.hyperledger.identus.iam.authentication.AuthenticationError
import org.hyperledger.identus.iam.authentication.AuthenticationError.AuthenticationMethodNotEnabled
import org.hyperledger.identus.iam.authorization.core.PermissionManagementService
import org.hyperledger.identus.iam.authorization.core.PermissionManagementServiceError.PermissionNotAvailable
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext}
import zio.*

import java.util.UUID

class KeycloakAuthenticatorImpl(
    client: KeycloakClient,
    keycloakConfig: KeycloakConfig,
    keycloakPermissionService: PermissionManagementService[KeycloakEntity],
) extends KeycloakAuthenticator {

  override def isEnabled: Boolean = keycloakConfig.enabled

  override def authenticate(token: String): IO[AuthenticationError, KeycloakEntity] = {
    if (isEnabled) {
      for {
        accessToken <- ZIO
          .fromEither(AccessToken.fromString(token, keycloakConfig.rolesClaimPathSegments))
          .mapError(AuthenticationError.InvalidCredentials.apply)
        introspection <- client
          .introspectToken(accessToken)
          .mapError(e => AuthenticationError.UnexpectedError(e.message))
        _ <- ZIO
          .fail(AuthenticationError.InvalidCredentials("The accessToken is invalid."))
          .unless(introspection.active)
        entityId <- ZIO
          .fromOption(introspection.sub)
          .mapError(_ => AuthenticationError.UnexpectedError("Subject ID is not found in the accessToken."))
          .flatMap { id =>
            ZIO
              .attempt(UUID.fromString(id))
              .mapError(e => AuthenticationError.UnexpectedError(s"Subject ID in accessToken is not a UUID. $e"))
          }
      } yield KeycloakEntity(entityId, accessToken = Some(accessToken))
    } else ZIO.fail(AuthenticationMethodNotEnabled("Keycloak authentication is not enabled"))
  }

  override def authorizeWalletAccessLogic(entity: KeycloakEntity): IO[AuthenticationError, WalletAccessContext] = {
    for {
      walletId <- keycloakPermissionService
        .listWalletPermissions(entity)
        .mapError {
          case PermissionNotAvailable(_, msg) => AuthenticationError.InvalidCredentials(msg)
          case e                              => AuthenticationError.UnexpectedError(e.userFacingMessage)
        }
        .flatMap {
          case head +: Nil => ZIO.succeed(head)
          case Nil         =>
            ZIO.fail(AuthenticationError.ResourceNotPermitted("No wallet permissions found."))
          case ls =>
            ZIO.fail(
              AuthenticationError.UnexpectedError("Too many wallet access granted, the wallet access is ambiguous.")
            )
        }
        .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
    } yield WalletAccessContext(walletId)
  }

  override def authorizeWalletAdmin(entity: KeycloakEntity): IO[AuthenticationError, WalletAdministrationContext] = {
    val selfServiceCtx = keycloakPermissionService
      .listWalletPermissions(entity)
      .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      .mapBoth(
        e => AuthenticationError.UnexpectedError(e.userFacingMessage),
        wallets => WalletAdministrationContext.SelfService(wallets)
      )

    for {
      role <- ZIO
        .fromOption(entity.accessToken)
        .mapError(_ => AuthenticationError.InvalidCredentials("AccessToken is missing."))
        .map(_.role.left.map(AuthenticationError.InvalidCredentials(_)))
        .absolve
      ctx <- role match {
        case EntityRole.Admin         => ZIO.succeed(WalletAdministrationContext.Admin())
        case EntityRole.Tenant        => selfServiceCtx
        case EntityRole.ExternalParty =>
          ZIO.fail(AuthenticationError.InvalidRole("External party cannot access the wallet."))
      }
    } yield ctx
  }
}

object KeycloakAuthenticatorImpl {
  val layer: RLayer[
    KeycloakClient & KeycloakConfig & PermissionManagementService[KeycloakEntity],
    KeycloakAuthenticator
  ] =
    ZLayer.fromFunction(KeycloakAuthenticatorImpl(_, _, _))

  val disabled: ULayer[KeycloakAuthenticator] =
    ZLayer.succeed {
      val notEnabledError = ZIO.fail(AuthenticationMethodNotEnabled("Keycloak authentication is not enabled"))
      new KeycloakAuthenticator {
        override def isEnabled: Boolean = false
        override def authenticate(token: String): IO[AuthenticationError, KeycloakEntity] = notEnabledError
        override def authorizeWalletAccessLogic(entity: KeycloakEntity): IO[AuthenticationError, WalletAccessContext] =
          notEnabledError
        override def authorizeWalletAdmin(
            entity: KeycloakEntity
        ): IO[AuthenticationError, WalletAdministrationContext] =
          notEnabledError
      }
    }
}
