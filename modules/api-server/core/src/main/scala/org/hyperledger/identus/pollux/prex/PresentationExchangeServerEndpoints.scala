package org.hyperledger.identus.pollux.prex

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.pollux.prex.controller.PresentationExchangeController
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class PresentationExchangeServerEndpoints(
    controller: PresentationExchangeController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  private val getPresentationDefinitionServerEndpoint: ZServerEndpoint[Any, Any] =
    PresentationExchangeEndpoints.getPresentationDefinition
      .zServerLogic { case (rc, id) =>
        controller.getPresentationDefinition(id).logTrace(rc)
      }

  private val listPresentationDefinitionServerEndpoint: ZServerEndpoint[Any, Any] =
    PresentationExchangeEndpoints.listPresentationDefinition
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, pagination) =>
          controller
            .listPresentationDefinition(pagination)(rc)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  private val createPresentationDefinitionServerEndpoint: ZServerEndpoint[Any, Any] =
    PresentationExchangeEndpoints.createPresentationDefinition
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, pd) =>
          controller
            .createPresentationDefinition(pd)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    getPresentationDefinitionServerEndpoint,
    listPresentationDefinitionServerEndpoint,
    createPresentationDefinitionServerEndpoint
  )
}

object PresentationExchangeServerEndpoints {
  def all: URIO[DefaultAuthenticator & PresentationExchangeController, List[ZServerEndpoint[Any, Any]]] = {
    for {
      controller <- ZIO.service[PresentationExchangeController]
      authenticator <- ZIO.service[DefaultAuthenticator]
      endpoints = PresentationExchangeServerEndpoints(controller, authenticator, authenticator)
    } yield endpoints.all
  }
}
