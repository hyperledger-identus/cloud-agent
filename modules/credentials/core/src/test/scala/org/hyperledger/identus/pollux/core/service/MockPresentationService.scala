package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.presentproof.{
  PresentCredentialRequestFormat,
  Presentation,
  ProofType,
  ProposePresentation,
  RequestPresentation
}
import org.hyperledger.identus.pollux.anoncreds.AnoncredPresentation
import org.hyperledger.identus.pollux.core.model.{DidCommID, PresentationRecord}
import org.hyperledger.identus.pollux.core.model.error.PresentationError
import org.hyperledger.identus.pollux.core.model.presentation.Options
import org.hyperledger.identus.pollux.core.service.serdes.{AnoncredCredentialProofsV1, AnoncredPresentationRequestV1}
import org.hyperledger.identus.pollux.sdjwt.{HolderPrivateKey, PresentationCompact}
import org.hyperledger.identus.pollux.vc.jwt.{Issuer, PresentationPayload, W3cCredentialPayload}
import org.hyperledger.identus.shared.models.*
import zio.{mock, Duration, IO, UIO, URIO, URLayer, ZIO, ZLayer}
import zio.json.*
import zio.mock.{Mock, Proxy}

import java.time.Instant
import java.util.UUID

object MockPresentationService extends Mock[PresentationService] {

  object CreateJwtPresentationRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            Option[String],
            Seq[ProofType],
            Option[Options],
            PresentCredentialRequestFormat,
            Option[String],
            Option[String],
            Option[Duration]
        ),
        PresentationError,
        PresentationRecord
      ]
  object CreateSDJWTPresentationRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            Option[String],
            Seq[ProofType],
            ast.Json.Obj,
            Option[Options],
            PresentCredentialRequestFormat,
            Option[String],
            Option[String],
            Option[Duration]
        ),
        PresentationError,
        PresentationRecord
      ]

  object CreateAnoncredPresentationRecord
      extends Effect[
        (
            DidId,
            Option[DidId],
            DidCommID,
            Option[String],
            AnoncredPresentationRequestV1,
            PresentCredentialRequestFormat,
            Option[String],
            Option[String],
            Option[Duration]
        ),
        PresentationError,
        PresentationRecord
      ]

  object MarkRequestPresentationSent extends Effect[DidCommID, PresentationError, PresentationRecord]

  object ReceivePresentation extends Effect[Presentation, PresentationError, PresentationRecord]

  object MarkPresentationVerified extends Effect[DidCommID, PresentationError, PresentationRecord]

  object MarkPresentationInvitationExpired extends Effect[DidCommID, PresentationError, PresentationRecord]

  object MarkPresentationAccepted extends Effect[DidCommID, PresentationError, PresentationRecord]

  object MarkPresentationRejected extends Effect[DidCommID, PresentationError, PresentationRecord]

  object MarkPresentationVerificationFailed extends Effect[DidCommID, PresentationError, PresentationRecord]

  object VerifyAnoncredPresentation extends Effect[DidCommID, PresentationError, PresentationRecord]

  object AcceptRequestPresentation extends Effect[(DidCommID, Seq[String]), PresentationError, PresentationRecord]

  object AcceptRequestPresentationInvitation extends Effect[(DidId, String), PresentationError, RequestPresentation]

  object AcceptSDJWTRequestPresentation
      extends Effect[(DidCommID, Seq[String], Option[ast.Json.Obj]), PresentationError, PresentationRecord]

  object AcceptAnoncredRequestPresentation
      extends Effect[
        (DidCommID, AnoncredCredentialProofsV1),
        PresentationError,
        PresentationRecord
      ]

  object RejectRequestPresentation extends Effect[DidCommID, PresentationError, PresentationRecord]

  object MarkPresentationGenerated extends Effect[(DidCommID, Presentation), PresentationError, PresentationRecord]

  object MarkPresentationSent extends Effect[DidCommID, PresentationError, PresentationRecord]

  object AcceptPresentation extends Effect[DidCommID, PresentationError, PresentationRecord]

  object RejectPresentation extends Effect[DidCommID, PresentationError, PresentationRecord]

  object ReceiveRequestPresentation
      extends Effect[(Option[String], RequestPresentation), PresentationError, PresentationRecord]

  override val compose: URLayer[mock.Proxy, PresentationService] = ZLayer {
    for {
      proxy <- ZIO.service[Proxy]
    } yield new PresentationService {

      override def createJwtPresentationRecord(
          pairwiseVerifierDID: DidId,
          pairwiseProverDID: Option[DidId],
          thid: DidCommID,
          connectionId: Option[String],
          proofTypes: Seq[ProofType],
          options: Option[Options],
          presentationFormat: PresentCredentialRequestFormat,
          goalCode: Option[String],
          goal: Option[String],
          expirationTime: Option[Duration]
      ): IO[PresentationError, PresentationRecord] =
        proxy(
          CreateJwtPresentationRecord,
          (
            pairwiseVerifierDID,
            pairwiseProverDID,
            thid,
            connectionId,
            proofTypes,
            options,
            presentationFormat,
            goalCode,
            goal,
            expirationTime
          )
        )

      override def createSDJWTPresentationRecord(
          pairwiseVerifierDID: DidId,
          pairwiseProverDID: Option[DidId],
          thid: DidCommID,
          connectionId: Option[String],
          proofTypes: Seq[ProofType],
          claimsToDisclose: ast.Json.Obj,
          options: Option[org.hyperledger.identus.pollux.core.model.presentation.Options],
          presentationFormat: PresentCredentialRequestFormat,
          goalCode: Option[String],
          goal: Option[String],
          expirationTime: Option[Duration]
      ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] =
        proxy(
          CreateSDJWTPresentationRecord,
          (
            pairwiseVerifierDID,
            pairwiseProverDID,
            thid,
            connectionId,
            proofTypes,
            claimsToDisclose,
            options,
            presentationFormat,
            goalCode,
            goal,
            expirationTime
          )
        )

      override def createAnoncredPresentationRecord(
          pairwiseVerifierDID: DidId,
          pairwiseProverDID: Option[DidId],
          thid: DidCommID,
          connectionId: Option[String],
          presentationRequest: AnoncredPresentationRequestV1,
          presentationFormat: PresentCredentialRequestFormat,
          goalCode: Option[String],
          goal: Option[String],
          expirationTime: Option[Duration]
      ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = {
        proxy(
          CreateAnoncredPresentationRecord,
          (
            pairwiseVerifierDID,
            pairwiseProverDID,
            thid,
            connectionId,
            presentationRequest,
            presentationFormat,
            goalCode,
            goal,
            expirationTime
          )
        )
      }

      override def acceptRequestPresentation(
          recordId: DidCommID,
          credentialsToUse: Seq[String]
      ): IO[PresentationError, PresentationRecord] =
        proxy(AcceptRequestPresentation, (recordId, credentialsToUse))

      override def acceptAnoncredRequestPresentation(
          recordId: DidCommID,
          credentialsToUse: AnoncredCredentialProofsV1
      ): IO[PresentationError, PresentationRecord] =
        proxy(AcceptAnoncredRequestPresentation, (recordId, credentialsToUse))

      def acceptSDJWTRequestPresentation(
          recordId: DidCommID,
          credentialsToUse: Seq[String],
          claimsToDisclose: Option[ast.Json.Obj]
      ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] =
        proxy(AcceptSDJWTRequestPresentation, (recordId, credentialsToUse, claimsToDisclose))

      override def rejectRequestPresentation(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(RejectRequestPresentation, recordId)

      override def receivePresentation(presentation: Presentation): IO[PresentationError, PresentationRecord] =
        proxy(ReceivePresentation, presentation)

      override def markRequestPresentationSent(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkRequestPresentationSent, recordId)

      override def markPresentationSent(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationSent, recordId)

      override def markPresentationGenerated(
          recordId: DidCommID,
          presentation: Presentation
      ): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationGenerated, (recordId, presentation))

      override def markPresentationVerified(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationVerified, recordId)

      override def markPresentationInvitationExpired(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationInvitationExpired, recordId)

      override def markPresentationRejected(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationRejected, recordId)

      override def markPresentationAccepted(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationAccepted, recordId)

      override def markPresentationVerificationFailed(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(MarkPresentationVerificationFailed, recordId)

      override def verifyAnoncredPresentation(
          presentation: Presentation,
          requestPresentation: RequestPresentation,
          recordId: DidCommID
      ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] =
        proxy(VerifyAnoncredPresentation, recordId)

      override def receiveRequestPresentation(
          connectionId: Option[String],
          request: RequestPresentation
      ): IO[PresentationError, PresentationRecord] =
        proxy(ReceiveRequestPresentation, (connectionId, request))

      override def acceptPresentation(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(AcceptPresentation, recordId)

      override def rejectPresentation(recordId: DidCommID): IO[PresentationError, PresentationRecord] =
        proxy(RejectPresentation, recordId)

      override def extractIdFromCredential(credential: W3cCredentialPayload): Option[UUID] = ???

      override def getPresentationRecords(
          ignoreWithZeroRetries: Boolean
      ): IO[PresentationError, Seq[PresentationRecord]] = ???

      override def createJwtPresentationPayloadFromRecord(
          record: DidCommID,
          issuer: Issuer,
          issuanceDate: Instant
      ): IO[PresentationError, PresentationPayload] = ???

      override def createPresentationFromRecord(
          record: DidCommID
      ): IO[PresentationError, PresentationCompact] = ???

      def createSDJwtPresentation(
          recordId: DidCommID,
          requestPresentation: RequestPresentation,
          optionalHolderPrivateKey: Option[HolderPrivateKey],
      ): ZIO[WalletAccessContext, PresentationError, Presentation] = ???

      override def createAnoncredPresentationPayloadFromRecord(
          record: DidCommID,
          anoncredCredentialProof: AnoncredCredentialProofsV1,
          issuanceDate: Instant
      ): IO[PresentationError, AnoncredPresentation] = ???

      override def createAnoncredPresentation(
          requestPresentation: RequestPresentation,
          recordId: DidCommID,
          anoncredCredentialProof: AnoncredCredentialProofsV1,
          issuanceDate: Instant
      ): ZIO[WalletAccessContext, PresentationError, Presentation] = ???

      override def getPresentationRecordsByStates(
          ignoreWithZeroRetries: Boolean,
          limit: Int,
          state: PresentationRecord.ProtocolState*
      ): IO[PresentationError, Seq[PresentationRecord]] = ???

      override def getPresentationRecordsByStatesForAllWallets(
          ignoreWithZeroRetries: Boolean,
          limit: Int,
          state: PresentationRecord.ProtocolState*
      ): IO[PresentationError, Seq[PresentationRecord]] = ???

      override def findPresentationRecord(recordId: DidCommID): URIO[WalletAccessContext, Option[PresentationRecord]] =
        ???

      def updateWithSDJWTDisclosedClaims(
          recordId: DidCommID,
          claimsDisclosed: ast.Json
      ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = ???

      override def findPresentationRecordByThreadId(
          thid: DidCommID
      ): IO[PresentationError, Option[PresentationRecord]] =
        ???

      override def receiveProposePresentation(request: ProposePresentation): IO[PresentationError, PresentationRecord] =
        ???

      override def acceptProposePresentation(recordId: DidCommID): IO[PresentationError, PresentationRecord] = ???

      override def markRequestPresentationRejected(recordId: DidCommID): IO[PresentationError, PresentationRecord] = ???

      override def markProposePresentationSent(recordId: DidCommID): IO[PresentationError, PresentationRecord] = ???

      override def reportProcessingFailure(
          recordId: DidCommID,
          failReason: Option[Failure]
      ): UIO[Unit] = ???

      override def getRequestPresentationFromInvitation(
          pairwiseProverDID: DidId,
          invitation: String
      ): IO[PresentationError, RequestPresentation] =
        proxy(AcceptRequestPresentationInvitation, (pairwiseProverDID, invitation))
    }
  }

}
