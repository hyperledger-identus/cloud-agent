package org.hyperledger.identus.presentproof.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.presentproof.controller.http.{
  AcceptRequestPresentationInvitation,
  RequestPresentationAction,
  RequestPresentationInput
}
import org.hyperledger.identus.presentproof.controller.PresentProofEndpoints.{
  acceptRequestPresentationInvitation,
  createOOBRequestPresentationInvitation,
  getAllPresentations,
  getPresentation,
  requestPresentation,
  updatePresentation
}
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class PresentProofServerEndpoints(
    presentProofController: PresentProofController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {
  private val requestPresentationEndpoint: ZServerEndpoint[Any, Any] =
    requestPresentation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: RequestPresentationInput) =>
          presentProofController
            .requestPresentation(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val getAllPresentationsEndpoint: ZServerEndpoint[Any, Any] =
    getAllPresentations
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, paginationInput: PaginationInput, thid: Option[String]) =>
          presentProofController
            .getPresentations(paginationInput, thid)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val getPresentationEndpoint: ZServerEndpoint[Any, Any] =
    getPresentation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, presentationId: UUID) =>
          presentProofController
            .getPresentation(presentationId)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val updatePresentationEndpoint: ZServerEndpoint[Any, Any] =
    updatePresentation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, presentationId: UUID, action: RequestPresentationAction) =>
          presentProofController
            .updatePresentation(presentationId, action)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val createOOBRequestPresentationInvitationEndpoint: ZServerEndpoint[Any, Any] =
    createOOBRequestPresentationInvitation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, action: RequestPresentationInput) =>
          presentProofController
            .createOOBRequestPresentationInvitation(action)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val acceptRequestPresentationInvitationEndpoint: ZServerEndpoint[Any, Any] =
    acceptRequestPresentationInvitation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, action: AcceptRequestPresentationInvitation) =>
          presentProofController
            .acceptRequestPresentationInvitation(action)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    requestPresentationEndpoint,
    getAllPresentationsEndpoint,
    getPresentationEndpoint,
    updatePresentationEndpoint,
    createOOBRequestPresentationInvitationEndpoint,
    acceptRequestPresentationInvitationEndpoint
  )
}

object PresentProofServerEndpoints {
  def all: URIO[PresentProofController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      presentProofController <- ZIO.service[PresentProofController]
      presentProofEndpoints = new PresentProofServerEndpoints(presentProofController, authenticator, authenticator)
    } yield presentProofEndpoints.all
  }
}
