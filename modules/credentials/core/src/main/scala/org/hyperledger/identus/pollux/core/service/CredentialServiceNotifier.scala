package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.castor.core.model.did.{CanonicalPrismDID, PrismDID, VerificationRelationship}
import org.hyperledger.identus.event.notification.*
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.issuecredential.{IssueCredential, OfferCredential, RequestCredential}
import org.hyperledger.identus.pollux.core.model.{DidCommID, IssueCredentialRecord}
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError.*
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchemaRef
import org.hyperledger.identus.pollux.core.repository.CredentialRepository
import org.hyperledger.identus.pollux.vc.jwt.Issuer
import org.hyperledger.identus.shared.models.*
import zio.{Duration, UIO, URIO, URLayer, ZIO, ZLayer}
import zio.json.ast.Json

import java.util.UUID

class CredentialServiceNotifier(
    svc: CredentialService,
    eventNotificationService: EventNotificationService,
    credentialRepository: CredentialRepository,
) extends CredentialService {

  private val issueCredentialRecordUpdatedEvent = "IssueCredentialRecordUpdated"

  override def createJWTIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      kidIssuer: Option[KeyId],
      thid: DidCommID,
      credentialSchemaRef: Option[CredentialSchemaRef],
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID,
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
      domain: String
  ): URIO[WalletAccessContext, IssueCredentialRecord] =
    notifyOnSuccess(
      svc.createJWTIssueCredentialRecord(
        pairwiseIssuerDID,
        pairwiseHolderDID,
        kidIssuer,
        thid,
        credentialSchemaRef,
        claims,
        validityPeriod,
        automaticIssuance,
        issuingDID,
        goalCode,
        goal,
        expirationDuration,
        connectionId,
        domain
      )
    )

  override def createSDJWTIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      kidIssuer: Option[KeyId],
      thid: DidCommID,
      credentialSchemaRef: Option[CredentialSchemaRef],
      claims: Json,
      validityPeriod: Option[Double] = None,
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID,
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
      domain: String
  ): URIO[WalletAccessContext, IssueCredentialRecord] =
    notifyOnSuccess(
      svc.createSDJWTIssueCredentialRecord(
        pairwiseIssuerDID,
        pairwiseHolderDID,
        kidIssuer,
        thid,
        credentialSchemaRef,
        claims,
        validityPeriod,
        automaticIssuance,
        issuingDID,
        goalCode,
        goal,
        expirationDuration,
        connectionId,
        domain
      )
    )

  override def createAnonCredsIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      thid: DidCommID,
      credentialDefinitionGUID: UUID,
      credentialDefinitionId: _root_.java.lang.String,
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
  ): URIO[WalletAccessContext, IssueCredentialRecord] =
    notifyOnSuccess(
      svc.createAnonCredsIssueCredentialRecord(
        pairwiseIssuerDID,
        pairwiseHolderDID,
        thid,
        credentialDefinitionGUID,
        credentialDefinitionId,
        claims,
        validityPeriod,
        automaticIssuance,
        goalCode,
        goal,
        expirationDuration,
        connectionId
      )
    )

  override def markOfferSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    notifyOnSuccess(svc.markOfferSent(recordId))

  override def receiveCredentialOffer(
      offer: OfferCredential
  ): ZIO[WalletAccessContext, InvalidCredentialOffer, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialOffer(offer))

  override def acceptCredentialOffer(
      recordId: DidCommID,
      subjectId: Option[String],
      keyId: Option[KeyId]
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
    notifyOnSuccess(svc.acceptCredentialOffer(recordId, subjectId, keyId))

  override def generateJWTCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateJWTCredentialRequest(recordId))

  override def generateSDJWTCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateSDJWTCredentialRequest(recordId))

  override def generateAnonCredsCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateAnonCredsCredentialRequest(recordId))

  override def markRequestSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    notifyOnSuccess(svc.markRequestSent(recordId))

  override def receiveCredentialRequest(
      request: RequestCredential
  ): ZIO[WalletAccessContext, InvalidCredentialRequest | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialRequest(request))

  override def acceptCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
    notifyOnSuccess(svc.acceptCredentialRequest(recordId))

  override def markCredentialSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markCredentialSent(recordId))

  override def markCredentialOfferInvitationExpired(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markCredentialOfferInvitationExpired(recordId))

  override def receiveCredentialIssue(
      issueCredential: IssueCredential
  ): ZIO[WalletAccessContext, InvalidCredentialIssue | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialIssue(issueCredential))

  override def generateJWTCredential(
      recordId: DidCommID,
      statusListRegistryUrl: String
  ): ZIO[WalletAccessContext, RecordNotFound | CredentialRequestValidationFailed, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateJWTCredential(recordId, statusListRegistryUrl))

  override def generateSDJWTCredential(
      recordId: DidCommID,
      expirationTime: Duration,
  ): ZIO[
    WalletAccessContext,
    RecordNotFound | ExpirationDateHasPassed | VCJwtHeaderParsingError,
    IssueCredentialRecord
  ] =
    notifyOnSuccess(svc.generateSDJWTCredential(recordId, expirationTime))

  override def generateAnonCredsCredential(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateAnonCredsCredential(recordId))

  private def notifyOnSuccess[R, E](
      effect: ZIO[R, E, IssueCredentialRecord]
  ) =
    for {
      record <- effect
      _ <- notify(record)
    } yield record

  private def notifyOnFail(record: IssueCredentialRecord) =
    notify(record)

  private def notify(record: IssueCredentialRecord) = {
    val result = for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      producer <- eventNotificationService.producer[IssueCredentialRecord]("Issue")
      _ <- producer.send(Event(issueCredentialRecordUpdatedEvent, record, walletId))
    } yield ()
    result.catchAll(e => ZIO.logError(s"Notification service error: $e"))
  }

  override def reportProcessingFailure(
      recordId: DidCommID,
      failReason: Option[Failure]
  ): URIO[WalletAccessContext, Unit] = for {
    ret <- svc.reportProcessingFailure(recordId, failReason)
    recordAfterFail <- credentialRepository.getById(recordId)
    _ <- notifyOnFail(recordAfterFail)
  } yield ret

  override def findById(
      recordId: DidCommID
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] =
    svc.findById(recordId)

  override def getById(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
    svc.getById(recordId)

  override def getIssueCredentialRecordByThreadId(
      thid: DidCommID,
      ignoreWithZeroRetries: Boolean
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] =
    svc.getIssueCredentialRecordByThreadId(thid, ignoreWithZeroRetries)

  override def getIssueCredentialRecords(
      ignoreWithZeroRetries: Boolean,
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): URIO[WalletAccessContext, (Seq[IssueCredentialRecord], Int)] =
    svc.getIssueCredentialRecords(ignoreWithZeroRetries, offset, limit)

  override def getIssueCredentialRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): URIO[WalletAccessContext, Seq[IssueCredentialRecord]] =
    svc.getIssueCredentialRecordsByStates(ignoreWithZeroRetries, limit, states*)

  override def getIssueCredentialRecordsByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): UIO[Seq[IssueCredentialRecord]] =
    svc.getIssueCredentialRecordsByStatesForAllWallets(ignoreWithZeroRetries, limit, states*)

  override def getJwtIssuer(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: Option[KeyId]
  ): URIO[WalletAccessContext, Issuer] =
    svc.getJwtIssuer(jwtIssuerDID, verificationRelationship, keyId)

  override def getCredentialOfferInvitation(
      pairwiseHolderDID: DidId,
      invitation: String
  ): ZIO[WalletAccessContext, CredentialServiceError, OfferCredential] =
    svc.getCredentialOfferInvitation(pairwiseHolderDID, invitation)
}

object CredentialServiceNotifier {
  val layer: URLayer[CredentialService & EventNotificationService & CredentialRepository, CredentialServiceNotifier] =
    ZLayer.fromFunction(CredentialServiceNotifier(_, _, _))
}
