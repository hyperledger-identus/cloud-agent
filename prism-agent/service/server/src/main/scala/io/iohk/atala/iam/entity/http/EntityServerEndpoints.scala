package io.iohk.atala.iam.entity.http

import io.iohk.atala.LogUtils.*
import io.iohk.atala.agent.walletapi.model.BaseEntity
import io.iohk.atala.agent.walletapi.model.EntityRole
import io.iohk.atala.api.http.model.PaginationInput
import io.iohk.atala.api.http.{ErrorResponse, RequestContext}
import io.iohk.atala.iam.authentication.Authenticator
import io.iohk.atala.iam.authentication.DefaultAuthenticator
import io.iohk.atala.iam.authentication.SecurityLogic
import io.iohk.atala.iam.authentication.admin.{AdminApiKeyCredentials}
import io.iohk.atala.iam.authentication.oidc.JwtCredentials
import io.iohk.atala.iam.entity.http.EntityEndpoints.*
import io.iohk.atala.iam.entity.http.controller.EntityController
import io.iohk.atala.iam.entity.http.model.{
  ApiKeyAuthenticationRequest,
  CreateEntityRequest,
  UpdateEntityNameRequest,
  UpdateEntityWalletIdRequest
}
import sttp.tapir.ztapir.*
import zio.{IO, URIO, ZIO}

import java.util.UUID

class EntityServerEndpoints(entityController: EntityController, authenticator: Authenticator[BaseEntity]) {

  private def adminRoleSecurityLogic(
      credentials: (AdminApiKeyCredentials, JwtCredentials)
  ): IO[ErrorResponse, BaseEntity] =
    SecurityLogic.authorizeRoleWith(credentials)(authenticator)(EntityRole.Admin)

  val createEntityServerEndpoint: ZServerEndpoint[Any, Any] = createEntityEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, request: CreateEntityRequest) =>
        entityController
          .createEntity(request)(rc)
          .logTrace(rc)
      }
    }

  val updateEntityNameServerEndpoint: ZServerEndpoint[Any, Any] = updateEntityNameEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, id: UUID, request: UpdateEntityNameRequest) =>
        entityController
          .updateEntityName(id, request.name)(rc)
          .logTrace(rc)
      }
    }

  val updateEntityWalletIdServerEndpoint: ZServerEndpoint[Any, Any] = updateEntityWalletIdEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, id: UUID, request: UpdateEntityWalletIdRequest) =>
        entityController
          .updateEntityWalletId(id, request.walletId)(rc)
          .logTrace(rc)
      }
    }

  val getEntityByIdServerEndpoint: ZServerEndpoint[Any, Any] = getEntityByIdEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, id: UUID) =>
        entityController
          .getEntity(id)(rc)
          .logTrace(rc)
      }
    }

  val getEntitiesServerEndpoint: ZServerEndpoint[Any, Any] = getEntitiesEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, paginationIn: PaginationInput) =>
        entityController
          .getEntities(paginationIn)(rc)
          .logTrace(rc)
      }
    }

  val deleteEntityByIdServerEndpoint: ZServerEndpoint[Any, Any] = deleteEntityByIdEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, id: UUID) =>
        entityController
          .deleteEntity(id)(rc)
          .logTrace(rc)
      }
    }

  val addEntityApiKeyAuthenticationServerEndpoint: ZServerEndpoint[Any, Any] = addEntityApiKeyAuthenticationEndpoint
    .zServerSecurityLogic(adminRoleSecurityLogic)
    .serverLogic {
      case entity => { case (rc: RequestContext, request: ApiKeyAuthenticationRequest) =>
        entityController
          .addApiKeyAuth(request.entityId, request.apiKey)(rc)
          .logTrace(rc)
      }
    }

  val deleteEntityApiKeyAuthenticationServerEndpoint: ZServerEndpoint[Any, Any] =
    deleteEntityApiKeyAuthenticationEndpoint
      .zServerSecurityLogic(adminRoleSecurityLogic)
      .serverLogic {
        case entity => { case (rc: RequestContext, request: ApiKeyAuthenticationRequest) =>
          entityController
            .deleteApiKeyAuth(request.entityId, request.apiKey)(rc)
            .logTrace(rc)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    createEntityServerEndpoint,
    updateEntityNameServerEndpoint,
    updateEntityWalletIdServerEndpoint,
    getEntityByIdServerEndpoint,
    getEntitiesServerEndpoint,
    deleteEntityByIdServerEndpoint,
    addEntityApiKeyAuthenticationServerEndpoint,
    deleteEntityApiKeyAuthenticationServerEndpoint
  )
}

object EntityServerEndpoints {
  def all: URIO[EntityController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      entityController <- ZIO.service[EntityController]
      auth <- ZIO.service[DefaultAuthenticator]
      entityEndpoints = new EntityServerEndpoints(entityController, auth)
    } yield entityEndpoints.all
  }
}
