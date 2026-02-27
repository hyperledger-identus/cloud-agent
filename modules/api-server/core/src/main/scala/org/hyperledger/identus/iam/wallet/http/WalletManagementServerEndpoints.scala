package org.hyperledger.identus.iam.wallet.http

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.iam.wallet.http.controller.WalletManagementController
import org.hyperledger.identus.shared.models.WalletAdministrationContext
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class WalletManagementServerEndpoints(
    controller: WalletManagementController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  val listWalletServerEndpoint: ZServerEndpoint[Any, Any] =
    WalletManagementEndpoints.listWallet
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAdminWith(_)(authenticator, authorizer))
      .serverLogic {
        case (_, wac) => { case (rc, paginationInput) =>
          controller
            .listWallet(paginationInput)(rc)
            .provide(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val getWalletServerEndpoint: ZServerEndpoint[Any, Any] =
    WalletManagementEndpoints.getWallet
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAdminWith(_)(authenticator, authorizer))
      .serverLogic {
        case (_, wac) => { case (rc, walletId) =>
          controller
            .getWallet(walletId)(rc)
            .provide(ZLayer.succeed(wac))
            .logTrace(rc)
        }

      }

  val createWalletServerEndpoint: ZServerEndpoint[Any, Any] =
    WalletManagementEndpoints.createWallet
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAdminWith(_)(authenticator, authorizer))
      .serverLogic {
        case (me, wac) => { case (rc, createWalletRequest) =>
          controller
            .createWallet(createWalletRequest, me)(rc)
            .provide(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val createWalletUmaPermissionServerEndpoint: ZServerEndpoint[Any, Any] =
    WalletManagementEndpoints.createWalletUmaPermmission
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAdminWith(_)(authenticator, authorizer))
      .serverLogic {
        case (_, wac) => { case (rc, walletId, request) =>
          controller
            .createWalletUmaPermission(walletId, request)(rc)
            .provide(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val deleteWalletUmaPermissionServerEndpoint: ZServerEndpoint[Any, Any] =
    WalletManagementEndpoints.deleteWalletUmaPermmission
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAdminWith(_)(authenticator, authorizer))
      .serverLogic {
        case (_, wac) => { case (rc, walletId, subject) =>
          controller
            .deleteWalletUmaPermission(walletId, subject)(rc)
            .provide(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  def all: List[ZServerEndpoint[Any, Any]] = List(
    listWalletServerEndpoint,
    getWalletServerEndpoint,
    createWalletServerEndpoint,
    createWalletUmaPermissionServerEndpoint,
    deleteWalletUmaPermissionServerEndpoint
  )

}

object WalletManagementServerEndpoints {
  def all: URIO[WalletManagementController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      walletManagementController <- ZIO.service[WalletManagementController]
      auth <- ZIO.service[DefaultAuthenticator]
      walletManagementServerEndpoints = WalletManagementServerEndpoints(walletManagementController, auth, auth)
    } yield walletManagementServerEndpoints.all
  }
}
