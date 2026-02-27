package org.hyperledger.identus.agent.server

import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.model.error.GetManagedDIDError
import org.hyperledger.identus.agent.walletapi.model.PublicationState.Published
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.castor.core.model.did.{LongFormPrismDID, PrismDID}
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError.{
  InvalidStateForOperation,
  RecordIdNotFound
}
import org.hyperledger.identus.connect.core.model.ConnectionRecord
import org.hyperledger.identus.connect.core.model.ConnectionRecord.{ProtocolState, Role}
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.mercury.model.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID
import scala.util.Try

trait ControllerHelper {

  protected case class DidIdPair(myDID: DidId, theirDid: DidId)

  private def extractDidIdPairFromEstablishedConnection(
      record: ConnectionRecord
  ): IO[InvalidStateForOperation, DidIdPair] = {
    (record.protocolState, record.connectionResponse, record.role) match {
      case (ProtocolState.ConnectionResponseReceived, Some(resp), Role.Invitee) =>
        // If Invitee, myDid is the target
        ZIO.succeed(DidIdPair(resp.to, resp.from))
      case (ProtocolState.ConnectionResponseSent, Some(resp), Role.Inviter) =>
        // If Inviter, myDid is the source
        ZIO.succeed(DidIdPair(resp.from, resp.to))
      case _ =>
        ZIO.fail(InvalidStateForOperation(record.protocolState))
    }
  }

  protected def getPairwiseDIDs(
      connectionId: UUID
  ): ZIO[WalletAccessContext & ConnectionService, RecordIdNotFound | InvalidStateForOperation, DidIdPair] = {
    for {
      connectionService <- ZIO.service[ConnectionService]
      maybeConnection <- connectionService.findRecordById(connectionId)
      connection <- ZIO.getOrFailWith(RecordIdNotFound(connectionId))(maybeConnection)
      didIdPair <- extractDidIdPairFromEstablishedConnection(connection)
    } yield didIdPair
  }

  protected def extractDidCommIdFromString(
      maybeDidCommId: String
  ): IO[ErrorResponse, org.hyperledger.identus.pollux.core.model.DidCommID] =
    ZIO
      .fromTry(Try(org.hyperledger.identus.pollux.core.model.DidCommID(maybeDidCommId)))
      .mapError(e => ErrorResponse.badRequest(detail = Some(s"Error parsing string as DidCommID: ${e.getMessage}")))

  protected def extractPrismDIDFromString(maybeDid: String): IO[ErrorResponse, PrismDID] =
    ZIO
      .fromEither(PrismDID.fromString(maybeDid))
      .mapError(e => ErrorResponse.badRequest(detail = Some(s"Error parsing string as PrismDID: $e")))

  protected def getLongFormPrismDID(
      did: PrismDID,
      allowUnpublishedIssuingDID: Boolean = false
  ): ZIO[WalletAccessContext & ManagedDIDService, GetManagedDIDError, Option[LongFormPrismDID]] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      maybeDIDState <- managedDIDService.getManagedDIDState(did.asCanonical).map {
        case Some(s) if !allowUnpublishedIssuingDID && s.publicationState != Published => None
        case s                                                                         => s
      }
      longFormPrismDID = maybeDIDState.map(ds => PrismDID.buildLongFormFromOperation(ds.createOperation))
    } yield longFormPrismDID
  }

}
