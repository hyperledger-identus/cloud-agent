package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.issue.controller.http.{
  AcceptCredentialOfferInvitation,
  AcceptCredentialOfferRequest,
  CreateIssueCredentialRecordRequest
}
import org.hyperledger.identus.issue.controller.IssueEndpoints.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class IssueServerEndpoints(
    issueController: IssueController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  val createCredentialOfferEndpoint: ZServerEndpoint[Any, Any] =
    createCredentialOffer
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: CreateIssueCredentialRecordRequest) =>
          issueController
            .createCredentialOffer(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }
  val createCredentialOfferInvitationEndpoint: ZServerEndpoint[Any, Any] =
    createCredentialOfferInvitation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: CreateIssueCredentialRecordRequest) =>
          issueController
            .createCredentialOfferInvitation(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val getCredentialRecordsEndpoint: ZServerEndpoint[Any, Any] =
    getCredentialRecords
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, paginationInput: PaginationInput, thid: Option[String]) =>
          issueController
            .getCredentialRecords(paginationInput, thid)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val getCredentialRecordEndpoint: ZServerEndpoint[Any, Any] =
    getCredentialRecord
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, recordId: String) =>
          issueController
            .getCredentialRecord(recordId)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val acceptCredentialOfferEndpoint: ZServerEndpoint[Any, Any] =
    acceptCredentialOffer
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, recordId: String, request: AcceptCredentialOfferRequest) =>
          issueController
            .acceptCredentialOffer(recordId, request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val acceptCredentialOfferInvitationEndpoint: ZServerEndpoint[Any, Any] =
    acceptCredentialOfferInvitation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: AcceptCredentialOfferInvitation) =>
          issueController
            .acceptCredentialOfferInvitation(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val issueCredentialEndpoint: ZServerEndpoint[Any, Any] =
    issueCredential
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, recordId: String) =>
          issueController
            .issueCredential(recordId)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    createCredentialOfferEndpoint,
    createCredentialOfferInvitationEndpoint,
    acceptCredentialOfferInvitationEndpoint,
    getCredentialRecordsEndpoint,
    getCredentialRecordEndpoint,
    acceptCredentialOfferEndpoint,
    issueCredentialEndpoint
  )

}

object IssueServerEndpoints {
  def all: URIO[IssueController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      issueController <- ZIO.service[IssueController]
      issueEndpoints = new IssueServerEndpoints(issueController, authenticator, authenticator)
    } yield issueEndpoints.all
  }
}
