package org.hyperledger.identus.iam.authorization

import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, Entity}
import org.hyperledger.identus.iam.authentication.oidc.KeycloakEntity
import org.hyperledger.identus.iam.authorization.core.{PermissionManagementService, PermissionManagementServiceError}
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import zio.*

class DefaultPermissionManagementService(
    entityPermission: PermissionManagementService[Entity],
    keycloakPermission: PermissionManagementService[KeycloakEntity]
) extends PermissionManagementService[BaseEntity] {

  def grantWalletToUser(
      walletId: WalletId,
      entity: BaseEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] = {
    entity match {
      case entity: Entity           => entityPermission.grantWalletToUser(walletId, entity)
      case kcEntity: KeycloakEntity => keycloakPermission.grantWalletToUser(walletId, kcEntity)
    }
  }

  def revokeWalletFromUser(
      walletId: WalletId,
      entity: BaseEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] = {
    entity match {
      case entity: Entity           => entityPermission.revokeWalletFromUser(walletId, entity)
      case kcEntity: KeycloakEntity => keycloakPermission.revokeWalletFromUser(walletId, kcEntity)
    }
  }

  def listWalletPermissions(
      entity: BaseEntity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Seq[WalletId]] = {
    entity match {
      case entity: Entity           => entityPermission.listWalletPermissions(entity)
      case kcEntity: KeycloakEntity => keycloakPermission.listWalletPermissions(kcEntity)
    }
  }

}

object DefaultPermissionManagementService {
  def layer: URLayer[
    PermissionManagementService[KeycloakEntity] & PermissionManagementService[Entity],
    PermissionManagementService[BaseEntity]
  ] =
    ZLayer.fromFunction(DefaultPermissionManagementService(_, _))
}
