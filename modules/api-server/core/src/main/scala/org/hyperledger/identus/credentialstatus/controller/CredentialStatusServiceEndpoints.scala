package org.hyperledger.identus.credentialstatus.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.credentialstatus.controller.CredentialStatusEndpoints.*
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.shared.models.WalletAccessContext
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class CredentialStatusServiceEndpoints(
    credentialStatusController: CredentialStatusController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  private val getCredentialStatusListById: ZServerEndpoint[Any, Any] =
    getCredentialStatusListEndpoint
      .zServerLogic { case (ctx: RequestContext, id: UUID) =>
        credentialStatusController
          .getStatusListCredentialById(id)(ctx)
      }

  private val revokeCredentialById: ZServerEndpoint[Any, Any] =
    revokeCredentialByIdEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, id: DidCommID) =>
          credentialStatusController
            .revokeCredentialById(id)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    getCredentialStatusListById,
    revokeCredentialById
  )
}

object CredentialStatusServiceEndpoints {
  def all: URIO[CredentialStatusController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      statusListController <- ZIO.service[CredentialStatusController]
      statusLisEndpoints = new CredentialStatusServiceEndpoints(statusListController, authenticator, authenticator)
    } yield statusLisEndpoints.all
  }
}
