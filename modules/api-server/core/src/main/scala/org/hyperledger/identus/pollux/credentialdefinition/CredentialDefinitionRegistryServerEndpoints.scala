package org.hyperledger.identus.pollux.credentialdefinition

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.pollux.credentialdefinition
import org.hyperledger.identus.pollux.credentialdefinition.controller.CredentialDefinitionController
import org.hyperledger.identus.pollux.credentialdefinition.http.{CredentialDefinitionInput, FilterInput}
import org.hyperledger.identus.pollux.credentialdefinition.CredentialDefinitionRegistryEndpoints.*
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class CredentialDefinitionRegistryServerEndpoints(
    config: AppConfig,
    credentialDefinitionController: CredentialDefinitionController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  object create {
    val http: ZServerEndpoint[Any, Any] = createCredentialDefinitionHttpUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, credentialDefinitionInput: CredentialDefinitionInput) =>
          credentialDefinitionController
            .createCredentialDefinition(credentialDefinitionInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }
    val did: ZServerEndpoint[Any, Any] = createCredentialDefinitionDidUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, credentialDefinitionInput: CredentialDefinitionInput) =>
          credentialDefinitionController
            .createCredentialDefinitionDidUrl(credentialDefinitionInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

    val all = List(http, did)
  }

  object get {
    val http: ZServerEndpoint[Any, Any] = getCredentialDefinitionByIdHttpUrlEndpoint.zServerLogic {
      case (ctx: RequestContext, guid: UUID) =>
        credentialDefinitionController
          .getCredentialDefinitionByGuid(guid)(ctx)
          .logTrace(ctx)
    }
    val did: ZServerEndpoint[Any, Any] = getCredentialDefinitionByIdDidUrlEndpoint.zServerLogic {
      case (ctx: RequestContext, guid: UUID) =>
        credentialDefinitionController
          .getCredentialDefinitionByGuidDidUrl(config.agent.httpEndpoint.serviceName, guid)(ctx)
          .logTrace(ctx)
    }

    val all = List(http, did)

  }

  object getMany {
    val http: ZServerEndpoint[Any, Any] = lookupCredentialDefinitionsByQueryHttpUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic {
        case wac => {
          case (ctx: RequestContext, filter: FilterInput, paginationInput: PaginationInput, order: Option[Order]) =>
            credentialDefinitionController
              .lookupCredentialDefinitions(
                filter,
                paginationInput.toPagination,
                order
              )(ctx)
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
        }
      }
    val did: ZServerEndpoint[Any, Any] = lookupCredentialDefinitionsByQueryDidUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic {
        case wac => {
          case (ctx: RequestContext, filter: FilterInput, paginationInput: PaginationInput, order: Option[Order]) =>
            credentialDefinitionController
              .lookupCredentialDefinitionsDidUrl(
                config.agent.httpEndpoint.serviceName,
                filter,
                paginationInput.toPagination,
                order
              )(ctx)
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
        }
      }

    val all = List(http, did)

  }

  object getRaw {
    val http: ZServerEndpoint[Any, Any] = getCredentialDefinitionInnerDefinitionByIdHttpUrlEndpoint.zServerLogic {
      case (ctx: RequestContext, guid: UUID) =>
        credentialDefinitionController
          .getCredentialDefinitionInnerDefinitionByGuid(guid)(ctx)
          .logTrace(ctx)
    }
    val did: ZServerEndpoint[Any, Any] = getCredentialDefinitionInnerDefinitionByIdDidUrlEndpoint.zServerLogic {
      case (ctx: RequestContext, guid: UUID) =>
        credentialDefinitionController
          .getCredentialDefinitionInnerDefinitionByGuidDidUrl(config.agent.httpEndpoint.serviceName, guid)(ctx)
          .logTrace(ctx)
    }

    val all = List(http, did)

  }

  val all: List[ZServerEndpoint[Any, Any]] =
    create.all ++ getMany.all ++ getRaw.all ++ get.all
}

object CredentialDefinitionRegistryServerEndpoints {
  def all: URIO[CredentialDefinitionController & DefaultAuthenticator & AppConfig, List[ZServerEndpoint[Any, Any]]] = {
    for {
      credentialDefinitionRegistryService <- ZIO.service[CredentialDefinitionController]
      authenticator <- ZIO.service[DefaultAuthenticator]
      config <- ZIO.service[AppConfig]
      credentialDefinitionRegistryEndpoints = new CredentialDefinitionRegistryServerEndpoints(
        config,
        credentialDefinitionRegistryService,
        authenticator,
        authenticator
      )
    } yield credentialDefinitionRegistryEndpoints.all
  }
}
