package org.hyperledger.identus.connect.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.connect.controller.http.{AcceptConnectionInvitationRequest, CreateConnectionRequest}
import org.hyperledger.identus.connect.controller.ConnectionEndpoints.*
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class ConnectionServerEndpoints(
    connectionController: ConnectionController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  private val createConnectionServerEndpoint: ZServerEndpoint[Any, Any] =
    createConnection
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: CreateConnectionRequest) =>
          connectionController
            .createConnection(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val getConnectionServerEndpoint: ZServerEndpoint[Any, Any] =
    getConnection
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, connectionId: UUID) =>
          connectionController
            .getConnection(connectionId)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val deleteConnectionServerEndpoint: ZServerEndpoint[Any, Any] =
    deleteConnection
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, connectionId: UUID) =>
          connectionController
            .deleteConnection(connectionId)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val getConnectionsServerEndpoint: ZServerEndpoint[Any, Any] =
    getConnections
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, paginationInput: PaginationInput, thid: Option[String]) =>
          connectionController
            .getConnections(paginationInput, thid)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  private val acceptConnectionInvitationServerEndpoint: ZServerEndpoint[Any, Any] =
    acceptConnectionInvitation
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: AcceptConnectionInvitationRequest) =>
          connectionController
            .acceptConnectionInvitation(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    createConnectionServerEndpoint,
    getConnectionServerEndpoint,
    deleteConnectionServerEndpoint,
    getConnectionsServerEndpoint,
    acceptConnectionInvitationServerEndpoint
  )
}

object ConnectionServerEndpoints {
  def all: URIO[ConnectionController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      connectionController <- ZIO.service[ConnectionController]
      connectionEndpoints = new ConnectionServerEndpoints(connectionController, authenticator, authenticator)
    } yield connectionEndpoints.all
  }
}
