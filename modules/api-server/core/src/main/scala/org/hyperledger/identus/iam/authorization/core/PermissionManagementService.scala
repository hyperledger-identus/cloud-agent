package org.hyperledger.identus.iam.authorization.core

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import zio.*

trait PermissionManagementService[E <: BaseEntity] {
  def grantWalletToUser(
      walletId: WalletId,
      entity: E
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit]
  def revokeWalletFromUser(
      walletId: WalletId,
      entity: E
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit]
  def listWalletPermissions(
      entity: E
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Seq[WalletId]]
}
