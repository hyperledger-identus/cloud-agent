package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.castor.core.model.did.{CanonicalPrismDID, PrismDID, VerificationRelationship}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.issuecredential.{IssueCredential, OfferCredential, RequestCredential}
import org.hyperledger.identus.pollux.core.model.{DidCommID, IssueCredentialRecord}
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError.*
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchemaRef
import org.hyperledger.identus.pollux.vc.jwt.Issuer
import org.hyperledger.identus.shared.models.*
import zio.{mock, Duration, IO, UIO, URIO, URLayer, ZIO, ZLayer}
import zio.json.ast.Json
import zio.mock.{Mock, Proxy}

import java.util.UUID

object MockCredentialService extends Mock[CredentialService] {

  object CreateJWTIssueCredentialRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            Option[CredentialSchemaRef],
            Json,
            Option[Double],
            Option[Boolean],
            CanonicalPrismDID,
            Option[String],
            Option[String],
            Option[Duration],
            Option[UUID]
        ),
        Nothing,
        IssueCredentialRecord
      ]
  object CreateSDJWTIssueCredentialRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            Option[CredentialSchemaRef],
            Json,
            Option[Double],
            Option[Boolean],
            CanonicalPrismDID,
            Option[String],
            Option[String],
            Option[Duration],
            Option[UUID]
        ),
        Nothing,
        IssueCredentialRecord
      ]

  object CreateAnonCredsIssueCredentialRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            UUID,
            Json,
            Option[Double],
            Option[Boolean],
            String,
            Option[String],
            Option[String],
            Option[Duration],
            Option[UUID]
        ),
        Nothing,
        IssueCredentialRecord
      ]

  object ReceiveCredentialOffer extends Effect[OfferCredential, InvalidCredentialOffer, IssueCredentialRecord]
  object AcceptCredentialOffer
      extends Effect[
        (DidCommID, Option[String], Option[KeyId]),
        RecordNotFound | UnsupportedDidFormat,
        IssueCredentialRecord
      ]
  object GenerateJWTCredentialRequest
      extends Effect[DidCommID, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord]
  object GenerateSDJWTCredentialRequest
      extends Effect[DidCommID, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord]
  object GenerateAnonCredsCredentialRequest extends Effect[DidCommID, RecordNotFound, IssueCredentialRecord]
  object ReceiveCredentialRequest
      extends Effect[
        RequestCredential,
        InvalidCredentialRequest | RecordNotFoundForThreadIdAndStates,
        IssueCredentialRecord
      ]
  object AcceptCredentialRequest extends Effect[DidCommID, RecordNotFound, IssueCredentialRecord]
  object GenerateJWTCredential
      extends Effect[(DidCommID, String), RecordNotFound | CredentialRequestValidationFailed, IssueCredentialRecord]
  object GenerateSDJWTCredential
      extends Effect[(DidCommID, Duration), RecordNotFound | ExpirationDateHasPassed, IssueCredentialRecord]
  object GenerateAnonCredsCredential extends Effect[DidCommID, RecordNotFound, IssueCredentialRecord]
  object ReceiveCredentialIssue
      extends Effect[
        IssueCredential,
        InvalidCredentialIssue | RecordNotFoundForThreadIdAndStates,
        IssueCredentialRecord
      ]
  object MarkOfferSent extends Effect[DidCommID, InvalidStateForOperation, IssueCredentialRecord]
  object MarkCredentialOfferInvitationExpired extends Effect[DidCommID, InvalidStateForOperation, IssueCredentialRecord]
  object MarkRequestSent extends Effect[DidCommID, InvalidStateForOperation, IssueCredentialRecord]
  object MarkCredentialSent extends Effect[DidCommID, InvalidStateForOperation, IssueCredentialRecord]
  object MarkCredentialPublicationPending extends Effect[DidCommID, CredentialServiceError, IssueCredentialRecord]
  object MarkCredentialPublicationQueued extends Effect[DidCommID, CredentialServiceError, IssueCredentialRecord]
  object MarkCredentialPublished extends Effect[DidCommID, CredentialServiceError, IssueCredentialRecord]
  object ReportProcessingFailure extends Effect[(DidCommID, Option[Failure]), Nothing, Unit]
  object GetCredentialOfferInvitation extends Effect[(DidId, String), CredentialServiceError, OfferCredential]
  override val compose: URLayer[mock.Proxy, CredentialService] = ZLayer {
    for {
      proxy <- ZIO.service[Proxy]
    } yield new CredentialService {

      override def getCredentialOfferInvitation(
          pairwiseHolderDID: DidId,
          invitation: String
      ): ZIO[WalletAccessContext, CredentialServiceError, OfferCredential] =
        proxy(GetCredentialOfferInvitation, pairwiseHolderDID, invitation)

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
        proxy(
          CreateJWTIssueCredentialRecord,
          pairwiseIssuerDID,
          pairwiseHolderDID,
          thid,
          credentialSchemaRef,
          claims,
          validityPeriod,
          automaticIssuance,
          issuingDID,
          goalCode,
          goal,
          expirationDuration,
          connectionId
        )

      override def createSDJWTIssueCredentialRecord(
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
        proxy(
          CreateSDJWTIssueCredentialRecord,
          pairwiseIssuerDID,
          pairwiseHolderDID,
          thid,
          credentialSchemaRef,
          claims,
          validityPeriod,
          automaticIssuance,
          issuingDID,
          goalCode,
          goal,
          expirationDuration,
          connectionId
        )

      override def createAnonCredsIssueCredentialRecord(
          pairwiseIssuerDID: DidId,
          pairwiseHolderDID: Option[DidId],
          thid: DidCommID,
          credentialDefinitionGUID: UUID,
          credentialDefinitionId: String,
          claims: Json,
          validityPeriod: Option[Double],
          automaticIssuance: Option[Boolean],
          goalCode: Option[String],
          goal: Option[String],
          expirationDuration: Option[Duration],
          connectionId: Option[UUID]
      ): URIO[WalletAccessContext, IssueCredentialRecord] =
        proxy(
          CreateAnonCredsIssueCredentialRecord,
          pairwiseIssuerDID,
          pairwiseHolderDID,
          thid,
          credentialDefinitionGUID,
          claims,
          validityPeriod,
          automaticIssuance,
          credentialDefinitionId,
          goalCode,
          goal,
          expirationDuration,
          connectionId
        )

      override def receiveCredentialOffer(offer: OfferCredential): IO[InvalidCredentialOffer, IssueCredentialRecord] =
        proxy(ReceiveCredentialOffer, offer)

      override def acceptCredentialOffer(
          recordId: DidCommID,
          subjectId: Option[String],
          keyId: Option[KeyId]
      ): IO[RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
        proxy(AcceptCredentialOffer, recordId, subjectId, keyId)

      override def generateJWTCredentialRequest(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
        proxy(GenerateJWTCredentialRequest, recordId)

      override def generateSDJWTCredentialRequest(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
        proxy(GenerateSDJWTCredentialRequest, recordId)

      override def generateAnonCredsCredentialRequest(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
        proxy(GenerateAnonCredsCredentialRequest, recordId)

      override def receiveCredentialRequest(
          request: RequestCredential
      ): IO[InvalidCredentialRequest | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] =
        proxy(ReceiveCredentialRequest, request)

      override def acceptCredentialRequest(recordId: DidCommID): IO[RecordNotFound, IssueCredentialRecord] =
        proxy(AcceptCredentialRequest, recordId)

      override def generateJWTCredential(
          recordId: DidCommID,
          statusListRegistryServiceName: String,
      ): ZIO[WalletAccessContext, RecordNotFound | CredentialRequestValidationFailed, IssueCredentialRecord] =
        proxy(GenerateJWTCredential, recordId, statusListRegistryServiceName)

      override def generateSDJWTCredential(
          recordId: DidCommID,
          expirationTime: Duration,
      ): ZIO[WalletAccessContext, RecordNotFound | ExpirationDateHasPassed, IssueCredentialRecord] =
        proxy(GenerateSDJWTCredential, recordId, expirationTime)

      override def generateAnonCredsCredential(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
        proxy(GenerateAnonCredsCredential, recordId)

      override def receiveCredentialIssue(
          issueCredential: IssueCredential
      ): IO[InvalidCredentialIssue | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] =
        proxy(ReceiveCredentialIssue, issueCredential)

      override def markOfferSent(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
        proxy(MarkOfferSent, recordId)

      override def markCredentialOfferInvitationExpired(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
        proxy(MarkCredentialOfferInvitationExpired, recordId)

      override def markRequestSent(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
        proxy(MarkRequestSent, recordId)

      override def markCredentialSent(
          recordId: DidCommID
      ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
        proxy(MarkCredentialSent, recordId)

      override def reportProcessingFailure(
          recordId: DidCommID,
          failReason: Option[Failure]
      ): URIO[WalletAccessContext, Unit] =
        proxy(ReportProcessingFailure, recordId, failReason)

      override def getIssueCredentialRecords(
          ignoreWithZeroRetries: Boolean,
          offset: Option[Int] = None,
          limit: Option[Int] = None
      ): URIO[WalletAccessContext, (Seq[IssueCredentialRecord], Int)] =
        ???

      override def getIssueCredentialRecordsByStates(
          ignoreWithZeroRetries: Boolean,
          limit: Int,
          states: IssueCredentialRecord.ProtocolState*
      ): URIO[WalletAccessContext, Seq[IssueCredentialRecord]] =
        ???

      override def getIssueCredentialRecordsByStatesForAllWallets(
          ignoreWithZeroRetries: Boolean,
          limit: Int,
          states: IssueCredentialRecord.ProtocolState*
      ): UIO[Seq[IssueCredentialRecord]] =
        ???

      override def findById(
          recordId: DidCommID
      ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] =
        ???

      override def getById(recordId: DidCommID): URIO[WalletAccessContext, IssueCredentialRecord] = ???

      override def getIssueCredentialRecordByThreadId(
          thid: DidCommID,
          ignoreWithZeroRetries: Boolean
      ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] = ???

      override def getJwtIssuer(
          jwtIssuerDID: PrismDID,
          verificationRelationship: VerificationRelationship,
          keyId: Option[KeyId]
      ): URIO[WalletAccessContext, Issuer] = ???
    }
  }
}
