package org.hyperledger.identus.connect.controller

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.connect.controller.http.{
  AcceptConnectionInvitationRequest,
  Connection,
  ConnectionsPage,
  CreateConnectionRequest
}
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
import org.hyperledger.identus.connect.core.model.ConnectionRecord.ProtocolState
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID
import scala.language.implicitConversions

class ConnectionControllerImpl(
    service: ConnectionService,
    managedDIDService: ManagedDIDService,
    appConfig: AppConfig
) extends ConnectionController {

  override def createConnection(
      request: CreateConnectionRequest
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Connection] = {
    for {
      pairwiseDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      connection <- service.createConnectionInvitation(request.label, request.goalCode, request.goal, pairwiseDid.did)
    } yield Connection.fromDomain(connection)
  }

  override def getConnection(
      connectionId: UUID
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Connection] = {
    for {
      maybeConnection <- service.findRecordById(connectionId)
      connection <- ZIO
        .fromOption(maybeConnection)
        .mapError(_ => ConnectionServiceError.RecordIdNotFound(connectionId))
    } yield Connection.fromDomain(connection)
  }

  override def deleteConnection(
      connectionId: UUID
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Unit] = {
    for {
      mayBeConnection <- service.findRecordById(connectionId)
      connection <- ZIO.fromOption(mayBeConnection).mapError(_ => ConnectionServiceError.RecordIdNotFound(connectionId))
      res <- connection.protocolState match
        case ProtocolState.InvitationGenerated | ProtocolState.InvitationExpired =>
          service.deleteRecordById(connection.id) *> ZIO.succeed(s"deleted connection ${connection.id} successful").unit
        case _ =>
          ZIO.fail(
            ErrorResponse(
              403,
              "Not Allowed",
              "Operation Forbidden",
              Some(s" ${ConnectionServiceError.InvalidStateForOperation}")
            )
          )
    } yield res
  }

  override def getConnections(
      paginationInput: PaginationInput,
      thid: Option[String]
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, ConnectionsPage] = {
    for {
      connections <- thid match
        case None       => service.findAllRecords()
        case Some(thid) => service.findRecordByThreadId(thid).map(_.toSeq)
    } yield ConnectionsPage(contents = connections.map(Connection.fromDomain))
  }

  override def acceptConnectionInvitation(
      request: AcceptConnectionInvitationRequest
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Connection] = {
    for {
      record <- service.receiveConnectionInvitation(request.invitation)
      pairwiseDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      connection <- service.acceptConnectionInvitation(record.id, pairwiseDid.did)
    } yield Connection.fromDomain(connection)
  }
}

object ConnectionControllerImpl {
  val layer: URLayer[ConnectionService & ManagedDIDService & AppConfig, ConnectionController] =
    ZLayer.fromFunction(ConnectionControllerImpl(_, _, _))
}
