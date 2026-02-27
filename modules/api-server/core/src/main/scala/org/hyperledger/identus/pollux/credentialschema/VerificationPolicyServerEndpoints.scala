package org.hyperledger.identus.pollux.credentialschema

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.pollux.credentialschema.controller.VerificationPolicyController
import org.hyperledger.identus.pollux.credentialschema.http.{VerificationPolicyInput, VerificationPolicyResponse}
import org.hyperledger.identus.pollux.credentialschema.VerificationPolicyEndpoints.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class VerificationPolicyServerEndpoints(
    controller: VerificationPolicyController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {
  def throwableToInternalServerError(throwable: Throwable) =
    ZIO.fail[ErrorResponse](ErrorResponse.internalServerError(detail = Option(throwable.getMessage)))

  // TODO: make the endpoint typed ZServerEndpoint[SchemaRegistryService, Any]
  val createVerificationPolicyServerEndpoint: ZServerEndpoint[Any, Any] =
    createVerificationPolicyEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, input: VerificationPolicyInput) =>
          controller
            .createVerificationPolicy(ctx, input)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val updateVerificationPolicyServerEndpoint: ZServerEndpoint[Any, Any] = {
    updateVerificationPolicyEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        {
          case (
                ctx: RequestContext,
                id: UUID,
                nonce: Int,
                update: VerificationPolicyInput
              ) =>
            controller
              .updateVerificationPolicyById(ctx, id, nonce, update)
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
        }
      }
  }

  val getVerificationPolicyByIdServerEndpoint: ZServerEndpoint[Any, Any] =
    getVerificationPolicyByIdEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, id: UUID) =>
          controller
            .getVerificationPolicyById(ctx, id)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val deleteVerificationPolicyByIdServerEndpoint: ZServerEndpoint[Any, Any] =
    deleteVerificationPolicyByIdEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, id: UUID) =>
          controller
            .deleteVerificationPolicyById(ctx, id)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val lookupVerificationPoliciesByQueryServerEndpoint: ZServerEndpoint[Any, Any] =
    lookupVerificationPoliciesByQueryEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        {
          case (
                ctx: RequestContext,
                filter: VerificationPolicyResponse.Filter,
                paginationInput: PaginationInput,
                order: Option[Order]
              ) =>
            controller
              .lookupVerificationPolicies(
                ctx,
                filter,
                paginationInput.toPagination,
                order
              )
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] =
    List(
      createVerificationPolicyServerEndpoint,
      getVerificationPolicyByIdServerEndpoint,
      updateVerificationPolicyServerEndpoint,
      deleteVerificationPolicyByIdServerEndpoint,
      lookupVerificationPoliciesByQueryServerEndpoint
    )
}

object VerificationPolicyServerEndpoints {
  def all: URIO[VerificationPolicyController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      controller <- ZIO.service[VerificationPolicyController]
      endpoints = new VerificationPolicyServerEndpoints(controller, authenticator, authenticator)
    } yield endpoints.all
  }
}
