package org.hyperledger.identus.iam.authorization.keycloak.admin

import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.agent.walletapi.service.WalletManagementService
import org.hyperledger.identus.iam.authentication.oidc.{KeycloakClient, KeycloakEntity}
import org.hyperledger.identus.iam.authorization.core.{PermissionManagementService, PermissionManagementServiceError}
import org.hyperledger.identus.iam.authorization.core.PermissionManagementServiceError.*
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import org.keycloak.authorization.client.AuthzClient
import org.keycloak.representations.idm.authorization.{ResourceRepresentation, UmaPermissionRepresentation}
import zio.*

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Try

case class KeycloakPermissionManagementService(
    authzClient: AuthzClient,
    keycloakClient: KeycloakClient,
    walletManagementService: WalletManagementService
) extends PermissionManagementService[KeycloakEntity] {

  private def walletResourceName(walletId: WalletId) = s"wallet-${walletId.toUUID.toString}"

  private def policyName(userId: String, resourceId: String) = s"user $userId on wallet $resourceId permission"

  override def grantWalletToUser(
      walletId: WalletId,
      entity: KeycloakEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] = {
    for {
      _ <- walletManagementService
        .findWallet(walletId)
        .someOrFail(WalletNotFoundById(walletId))

      walletResourceOpt <- findWalletResource(walletId)

      walletResource <- ZIO
        .fromOption(walletResourceOpt)
        .orElse(createWalletResource(walletId))
      _ <- ZIO.log(s"Wallet resource created ${walletResource.toString}")

      permission <- createResourcePermission(walletResource.getId, entity.id.toString)

      _ <- ZIO.log(s"Permission created with id ${permission.getId} and name ${permission.getName}")
    } yield ()
  }

  private def createResourcePermission(resourceId: String, userId: String): UIO[UmaPermissionRepresentation] = {
    val policy = UmaPermissionRepresentation()
    policy.setName(policyName(userId, resourceId))
    policy.setUsers(Set(userId).asJava)

    for {
      umaPermissionRepresentation <- ZIO
        .attemptBlocking(
          authzClient
            .protection()
            .policy(resourceId)
            .create(policy)
        )
        .orDie
    } yield umaPermissionRepresentation
  }

  private def findWalletResource(walletId: WalletId): UIO[Option[ResourceRepresentation]] = {
    for {
      walletResource <- ZIO
        .attemptBlocking(
          authzClient.protection().resource().findById(walletId.toUUID.toString)
        )
        .asSome
        .catchSome { case e: RuntimeException =>
          if (e.getMessage.contains("Could not find resource")) ZIO.none
          else ZIO.fail(e)
        }
        .orDie
    } yield walletResource
  }

  private def createWalletResource(walletId: WalletId): UIO[ResourceRepresentation] = {
    val walletResource = ResourceRepresentation()
    walletResource.setId(walletId.toUUID.toString)
    walletResource.setUris(Set(s"/wallets/${walletResourceName(walletId)}").asJava)
    walletResource.setName(walletResourceName(walletId))
    walletResource.setOwnerManagedAccess(true)

    for {
      _ <- ZIO.log(s"Creating resource for the wallet ${walletId.toUUID.toString}")
      response <- ZIO
        .attemptBlocking(
          authzClient
            .protection()
            .resource()
            .create(walletResource)
        )
        .orDie
      resource <- ZIO
        .attemptBlocking(
          authzClient
            .protection()
            .resource()
            .findById(walletResource.getId)
        )
        .orDie
      _ <- ZIO.log(s"Resource for the wallet created id: ${resource.getId}, name ${resource.getName}")
    } yield resource
  }

  override def revokeWalletFromUser(
      walletId: WalletId,
      entity: KeycloakEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] = {
    val userId = entity.id
    for {
      _ <- walletManagementService
        .findWallet(walletId)
        .someOrFail(WalletNotFoundById(walletId))

      walletResource <- findWalletResource(walletId).someOrFail(WalletNotFoundById(walletId))

      permissionOpt <- ZIO
        .attemptBlocking(
          authzClient
            .protection()
            .policy(walletResource.getId)
            .find(
              policyName(userId.toString, walletResource.getId),
              null,
              0,
              1
            )
        )
        .orDie
        .map(_.asScala.headOption)

      permission <- ZIO
        .fromOption(permissionOpt)
        .orElseFail(PermissionNotFoundById(userId, walletId, walletResource.getId))

      _ <- ZIO
        .attemptBlocking(
          authzClient
            .protection()
            .policy(walletResource.getId)
            .delete(permission.getId)
        )
        .orDie

      _ <- ZIO.log(
        s"Permission ${permission.getId} deleted for user ${userId.toString} and wallet ${walletResource.getId}"
      )
    } yield ()
  }

  override def listWalletPermissions(
      entity: KeycloakEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Seq[WalletId]] = {
    for {
      token <- ZIO
        .fromOption(entity.accessToken)
        .mapError(_ => ServiceError("AccessToken is missing for listing permissions."))
      tokenIsRpt <- ZIO.fromEither(token.isRpt).mapError(ServiceError.apply)
      rpt <-
        if (tokenIsRpt) ZIO.succeed(token)
        else if (keycloakClient.keycloakConfig.autoUpgradeToRPT) {
          keycloakClient
            .getRpt(token)
            .logError("Fail to obtail RPT for wallet permissions")
            .mapError(e => ServiceError(e.message))
        } else ZIO.fail(PermissionNotAvailable(entity.id, s"AccessToken is not RPT."))
      permittedResources <- keycloakClient
        .checkPermissions(rpt)
        .logError("Fail to list resource permissions on keycloak")
        .mapError(e => ServiceError(e.message))
      permittedWallet <- getPermittedWallet(permittedResources)
    } yield permittedWallet.map(_.id)
  }

  private def getPermittedWallet(
      resourceIds: Seq[String]
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Seq[Wallet]] = {
    val walletIds = resourceIds.flatMap(id => Try(UUID.fromString(id)).toOption).map(WalletId.fromUUID)
    walletManagementService
      .getWallets(walletIds)
  }
}

object KeycloakPermissionManagementService {
  val layer: URLayer[
    AuthzClient & KeycloakClient & WalletManagementService,
    PermissionManagementService[KeycloakEntity]
  ] =
    ZLayer.fromFunction(KeycloakPermissionManagementService(_, _, _))

  val disabled: ULayer[PermissionManagementService[KeycloakEntity]] =
    ZLayer.succeed {
      val notEnabledError = ZIO.fail(ServiceError("Keycloak is not enabled"))
      new PermissionManagementService[KeycloakEntity] {
        override def grantWalletToUser(
            walletId: WalletId,
            entity: KeycloakEntity
        ): IO[PermissionManagementServiceError, Unit] = notEnabledError

        override def revokeWalletFromUser(
            walletId: WalletId,
            entity: KeycloakEntity
        ): IO[PermissionManagementServiceError, Unit] = notEnabledError

        override def listWalletPermissions(
            entity: KeycloakEntity
        ): IO[PermissionManagementServiceError, Seq[WalletId]] = notEnabledError
      }
    }
}
