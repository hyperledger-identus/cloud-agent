package org.hyperledger.identus.iam.authorization.core

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.agent.walletapi.service.EntityService
import org.hyperledger.identus.iam.authorization.core.PermissionManagementServiceError.*
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import zio.*

import scala.language.implicitConversions

class EntityPermissionManagementService(entityService: EntityService) extends PermissionManagementService[Entity] {

  override def grantWalletToUser(
      walletId: WalletId,
      entity: Entity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] = {
    for {
      _ <- ZIO
        .serviceWith[WalletAdministrationContext](_.isAuthorized(walletId))
        .filterOrFail(identity)(WalletNotFoundById(walletId))
      _ <- entityService.assignWallet(entity.id, walletId.toUUID).orDieAsUnmanagedFailure
    } yield ()
  }

  override def revokeWalletFromUser(
      walletId: WalletId,
      entity: Entity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Unit] =
    ZIO.fail(ServiceError(s"Revoking wallet permission for an Entity is not yet supported."))

  override def listWalletPermissions(
      entity: Entity
  ): ZIO[WalletAdministrationContext, PermissionManagementServiceError, Seq[WalletId]] = {
    val walletId = WalletId.fromUUID(entity.walletId)
    ZIO
      .serviceWith[WalletAdministrationContext](_.isAuthorized(walletId))
      .filterOrFail(identity)(WalletNotFoundById(walletId))
      .as(Seq(walletId))
  }

}

object EntityPermissionManagementService {
  val layer: URLayer[EntityService, PermissionManagementService[Entity]] =
    ZLayer.fromFunction(EntityPermissionManagementService(_))
}
