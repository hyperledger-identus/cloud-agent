package io.iohk.atala.iam.authorization

import io.iohk.atala.agent.walletapi.model.BaseEntity
import io.iohk.atala.agent.walletapi.model.Entity
import io.iohk.atala.iam.authentication.oidc.KeycloakEntity
import io.iohk.atala.iam.authorization.core.PermissionManagement
import io.iohk.atala.iam.authorization.core.PermissionManagement.Error
import io.iohk.atala.shared.models.WalletAdministrationContext
import io.iohk.atala.shared.models.WalletId
import zio.*

class DefaultPermissionManagementService(
    entityPermission: PermissionManagement.Service[Entity],
    keycloakPermission: PermissionManagement.Service[KeycloakEntity]
) extends PermissionManagement.Service[BaseEntity] {

  def grantWalletToUser(walletId: WalletId, entity: BaseEntity): ZIO[WalletAdministrationContext, Error, Unit] = {
    entity match {
      case entity: Entity           => entityPermission.grantWalletToUser(walletId, entity)
      case kcEntity: KeycloakEntity => keycloakPermission.grantWalletToUser(walletId, kcEntity)
    }
  }

  def revokeWalletFromUser(walletId: WalletId, entity: BaseEntity): ZIO[WalletAdministrationContext, Error, Unit] = {
    entity match {
      case entity: Entity           => entityPermission.revokeWalletFromUser(walletId, entity)
      case kcEntity: KeycloakEntity => keycloakPermission.revokeWalletFromUser(walletId, kcEntity)
    }
  }

  def listWalletPermissions(entity: BaseEntity): ZIO[WalletAdministrationContext, Error, Seq[WalletId]] = {
    entity match {
      case entity: Entity           => entityPermission.listWalletPermissions(entity)
      case kcEntity: KeycloakEntity => keycloakPermission.listWalletPermissions(kcEntity)
    }
  }

}

object DefaultPermissionManagementService {
  def layer: URLayer[
    PermissionManagement.Service[KeycloakEntity] & PermissionManagement.Service[Entity],
    PermissionManagement.Service[BaseEntity]
  ] =
    ZLayer.fromFunction(DefaultPermissionManagementService(_, _))
}
