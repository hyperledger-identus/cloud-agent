package org.hyperledger.identus.credentials.credentialdefinition

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.credentials.credentialdefinition
import org.hyperledger.identus.credentials.credentialdefinition.controller.CredentialDefinitionController
import org.hyperledger.identus.credentials.credentialdefinition.http.{CredentialDefinitionInput, FilterInput}
import org.hyperledger.identus.credentials.credentialdefinition.CredentialDefinitionRegistryEndpoints.*
import org.hyperledger.identus.iam.authentication.{Authenticator, AuthenticatorWithAuthZ, Authorizer, SecurityLogic}
import org.hyperledger.identus.wallet.model.BaseEntity
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class CredentialDefinitionRegistryServerEndpoints(
    serviceName: String,
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
          .getCredentialDefinitionByGuidDidUrl(serviceName, guid)(ctx)
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
                serviceName,
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
          .getCredentialDefinitionInnerDefinitionByGuidDidUrl(serviceName, guid)(ctx)
          .logTrace(ctx)
    }

    val all = List(http, did)

  }

  val all: List[ZServerEndpoint[Any, Any]] =
    create.all ++ getMany.all ++ getRaw.all ++ get.all
}

object CredentialDefinitionRegistryServerEndpoints {
  def all(
      serviceName: String
  ): URIO[CredentialDefinitionController & AuthenticatorWithAuthZ[BaseEntity], List[
    ZServerEndpoint[Any, Any]
  ]] = {
    for {
      credentialDefinitionRegistryService <- ZIO.service[CredentialDefinitionController]
      authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
      credentialDefinitionRegistryEndpoints = new CredentialDefinitionRegistryServerEndpoints(
        serviceName,
        credentialDefinitionRegistryService,
        authenticator,
        authenticator
      )
    } yield credentialDefinitionRegistryEndpoints.all
  }
}
