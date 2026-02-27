package org.hyperledger.identus.didcomm.controller

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.DidCommHttpServerError.DIDCommMessageParsingError
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.didcomm.controller.http.DIDCommMessage
import org.hyperledger.identus.didcomm.controller.DIDCommControllerError.*
import org.hyperledger.identus.mercury.*
import org.hyperledger.identus.mercury.model.*
import org.hyperledger.identus.mercury.protocol.connection.{ConnectionRequest, ConnectionResponse}
import org.hyperledger.identus.mercury.protocol.issuecredential.*
import org.hyperledger.identus.mercury.protocol.presentproof.*
import org.hyperledger.identus.mercury.protocol.revocationnotificaiton.RevocationNotification
import org.hyperledger.identus.mercury.DidOps.*
import org.hyperledger.identus.pollux.core.model.error.{CredentialServiceError, PresentationError}
import org.hyperledger.identus.pollux.core.service.{CredentialService, PresentationService}
import org.hyperledger.identus.shared.models.{Failure, StatusCode, WalletAccessContext}
import zio.*
import zio.json.*

import java.util.UUID
import scala.language.implicitConversions

class DIDCommControllerImpl(
    didOps: DidOps,
    managedDIDService: ManagedDIDService,
    didNonSecretStorage: DIDNonSecretStorage,
    connectionService: ConnectionService,
    presentationService: PresentationService,
    credentialService: CredentialService,
    appConfig: AppConfig
) extends DIDCommController {

  override def handleDIDCommMessage(msg: DIDCommMessage)(using rc: RequestContext): IO[ErrorResponse, Unit] = {
    for {
      _ <- validateContentType(rc.request.contentType)
      _ <- handleMessage(msg)
    } yield ()
  }

  private def validateContentType(contentType: Option[String]) = {
    if (contentType.contains(MediaTypes.contentTypeEncrypted)) ZIO.unit
    else ZIO.fail(InvalidContentType(contentType))
  }

  private def handleMessage(msg: DIDCommMessage) = {
    ZIO.logAnnotate("request-id", UUID.randomUUID.toString) {
      for {
        msgAndContext <- unpackMessage(msg)
        _ <- processMessage(msgAndContext._1)
          .catchAll {
            case f: Failure                    => ZIO.fail(f)
            case _: DIDCommMessageParsingError => ZIO.fail(UnexpectedError(StatusCode.BadRequest))
          }
          .provideSomeLayer(ZLayer.succeed(msgAndContext._2))
      } yield ()
    }
  }

  private def processMessage = handleConnect orElse
    handleIssueCredential orElse
    handlePresentProof orElse
    revocationNotification orElse
    handleUnknownMessage

  private def unpackMessage(msg: DIDCommMessage): IO[DIDCommControllerError, (Message, WalletAccessContext)] =
    for {
      recipientDid <- ZIO
        .fromOption(msg.recipients.headOption.map(_.header.kid.split("#")(0)))
        .mapError(_ => RecipientNotFoundError)
      _ <- ZIO.logInfo(s"Extracted recipient Did => $recipientDid")
      didId = DidId(recipientDid)
      maybePeerDIDRecord <- didNonSecretStorage.getPeerDIDRecord(didId).orDie
      peerDIDRecord <- ZIO.fromOption(maybePeerDIDRecord).mapError(_ => PeerDIDNotFoundError(didId))
      _ <- ZIO.logInfo(s"PeerDID record successfully loaded in DIDComm receiver endpoint: $peerDIDRecord")
      walletAccessContext = WalletAccessContext(peerDIDRecord.walletId)
      peerDID <- managedDIDService
        .getPeerDID(didId)
        .mapError(e => PeerDIDKeyNotFoundError(e.didId, e.keyId))
        .provide(ZLayer.succeed(walletAccessContext))
      agent = AgentPeerService.makeLayer(peerDID)
      msg <- unpack(msg.toJson).provide(ZLayer.succeed(didOps), agent)
    } yield (msg.message, walletAccessContext)

  /*
   * Connect
   */
  private val handleConnect: PartialFunction[Message, ZIO[
    WalletAccessContext,
    DIDCommMessageParsingError | ConnectionServiceError,
    Unit
  ]] = {
    case msg if msg.piuri == ConnectionRequest.`type` =>
      for {
        connectionRequest <- ZIO
          .fromEither(ConnectionRequest.fromMessage(msg))
          .mapError(DIDCommMessageParsingError.apply)
        _ <- ZIO.logInfo("As an Inviter in connect got ConnectionRequest: " + connectionRequest)
        record <- connectionService.receiveConnectionRequest(
          connectionRequest,
          Some(appConfig.connect.connectInvitationExpiry)
        )
        _ <- connectionService.acceptConnectionRequest(record.id)
      } yield ()
    case msg if msg.piuri == ConnectionResponse.`type` =>
      for {
        connectionResponse <- ZIO
          .fromEither(ConnectionResponse.fromMessage(msg))
          .mapError(DIDCommMessageParsingError.apply)
        _ <- ZIO.logInfo("As an Invitee in connect got ConnectionResponse: " + connectionResponse)
        _ <- connectionService.receiveConnectionResponse(connectionResponse)
      } yield ()
  }

  /*
   * Issue Credential
   */
  private val handleIssueCredential
      : PartialFunction[Message, ZIO[WalletAccessContext, CredentialServiceError, Unit]] = {
    case msg if msg.piuri == OfferCredential.`type` =>
      for {
        offerFromIssuer <- ZIO
          .fromEither(OfferCredential.readFromMessage(msg))
          .mapError(CredentialServiceError.InvalidCredentialOffer(_))
        _ <- ZIO.logInfo("As an Holder in issue-credential got OfferCredential: " + offerFromIssuer)
        _ <- credentialService.receiveCredentialOffer(offerFromIssuer)
      } yield ()
    case msg if msg.piuri == RequestCredential.`type` =>
      for {
        requestCredential <- ZIO
          .fromEither(RequestCredential.readFromMessage(msg))
          .mapError(CredentialServiceError.InvalidCredentialRequest(_))
        _ <- ZIO.logInfo("As an Issuer in issue-credential got RequestCredential: " + requestCredential)
        _ <- credentialService.receiveCredentialRequest(requestCredential)
      } yield ()
    case msg if msg.piuri == IssueCredential.`type` =>
      for {
        issueCredential <- ZIO
          .fromEither(IssueCredential.readFromMessage(msg))
          .mapError(CredentialServiceError.InvalidCredentialIssue(_))
        _ <- ZIO.logInfo("As an Holder in issue-credential got IssueCredential: " + issueCredential)
        _ <- credentialService.receiveCredentialIssue(issueCredential)
      } yield ()
  }

  /*
   * Present Proof
   */
  private val handlePresentProof: PartialFunction[Message, ZIO[WalletAccessContext, PresentationError, Unit]] = {
    case msg if msg.piuri == ProposePresentation.`type` =>
      for {
        proposePresentation <- ZIO.succeed(ProposePresentation.readFromMessage(msg))
        _ <- ZIO.logInfo("As a Verifier in  present-proof got ProposePresentation: " + proposePresentation)
        _ <- presentationService.receiveProposePresentation(proposePresentation)
      } yield ()
    case msg if msg.piuri == RequestPresentation.`type` =>
      for {
        requestPresentation <- ZIO.succeed(RequestPresentation.readFromMessage(msg))
        _ <- ZIO.logInfo("As a Prover in present-proof got RequestPresentation: " + requestPresentation)
        _ <- presentationService.receiveRequestPresentation(None, requestPresentation)
      } yield ()
    case msg if msg.piuri == Presentation.`type` =>
      for {
        presentation <- ZIO.succeed(Presentation.readFromMessage(msg))
        _ <- ZIO.logInfo("As a Verifier in present-proof got Presentation: " + presentation)
        _ <- presentationService.receivePresentation(presentation)
      } yield ()
  }

  private val revocationNotification: PartialFunction[Message, ZIO[Any, DIDCommMessageParsingError, Unit]] = {
    case msg if msg.piuri == RevocationNotification.`type` =>
      for {
        revocationNotification <- ZIO
          .fromEither(RevocationNotification.readFromMessage(msg))
          .mapError(DIDCommMessageParsingError(_))
        _ <- ZIO.logInfo("Got RevocationNotification: " + revocationNotification)
      } yield ()
  }

  /*
   * Unknown Message
   */
  private val handleUnknownMessage: PartialFunction[Message, IO[UnsupportedPIURI, String]] = { case msg =>
    ZIO.fail(UnsupportedPIURI(msg.piuri))
  }
}

object DIDCommControllerImpl {
  val layer: URLayer[
    DidOps & ManagedDIDService & DIDNonSecretStorage & ConnectionService & CredentialService & PresentationService &
      AppConfig,
    DIDCommController
  ] =
    ZLayer.fromFunction(DIDCommControllerImpl(_, _, _, _, _, _, _))
}
