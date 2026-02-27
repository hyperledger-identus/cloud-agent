package org.hyperledger.identus.agent.server.jobs

import cats.syntax.all.*
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.jobs.BackgroundJobError.{
  ErrorResponseReceivedFromPeerAgent,
  InvalidState,
  NotImplemented
}
import org.hyperledger.identus.agent.walletapi.model.error.{DIDSecretStorageError, GetManagedDIDError}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.error.DIDResolutionError as CastorDIDResolutionError
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.mercury.*
import org.hyperledger.identus.mercury.model.*
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import org.hyperledger.identus.mercury.protocol.presentproof.*
import org.hyperledger.identus.mercury.protocol.reportproblem.v2.{ProblemCode, ReportProblem}
import org.hyperledger.identus.pollux.core.model.{presentation, *}
import org.hyperledger.identus.pollux.core.model.error.{CredentialServiceError, PresentationError}
import org.hyperledger.identus.pollux.core.model.error.PresentationError.*
import org.hyperledger.identus.pollux.core.model.presentation.Options
import org.hyperledger.identus.pollux.core.service.{CredentialService, PresentationService}
import org.hyperledger.identus.pollux.core.service.serdes.AnoncredCredentialProofsV1
import org.hyperledger.identus.pollux.sdjwt.{HolderPrivateKey, IssuerPublicKey, PresentationCompact, SDJWT}
import org.hyperledger.identus.pollux.vc.jwt.{
  CredentialSchemaAndTrustedIssuersConstraint,
  DidResolver as JwtDidResolver,
  Issuer as JwtIssuer,
  JWT,
  JwtPresentation
}
import org.hyperledger.identus.resolvers.DIDResolver
import org.hyperledger.identus.shared.http.*
import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.{Message, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{Failure, *}
import org.hyperledger.identus.shared.utils.aspects.CustomMetricsAspect
import org.hyperledger.identus.shared.utils.DurationOps.toMetricsSeconds
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.metrics.*
import zio.prelude.{Validation, ZValidation}
import zio.prelude.ZValidation.{Failure as ZFailure, *}

import java.time.{Instant, ZoneId}
import java.util.UUID

object PresentBackgroundJobs extends BackgroundJobsHelper {

  private type ERROR =
    /*DIDSecretStorageError | PresentationError | CredentialServiceError | BackgroundJobError | TransportError | */
    CastorDIDResolutionError | GetManagedDIDError | Failure

  private type RESOURCES = COMMON_RESOURCES & CredentialService & JwtDidResolver & UriResolver & DIDService &
    AppConfig & MESSAGING_RESOURCES

  private type COMMON_RESOURCES = PresentationService & DIDNonSecretStorage & ManagedDIDService

  private type MESSAGING_RESOURCES = DidOps & DIDResolver & HttpClient

  private val TOPIC_NAME = "present"

  val presentFlowsHandler = for {
    appConfig <- ZIO.service[AppConfig]
    _ <- messaging.MessagingService.consumeWithRetryStrategy(
      "identus-cloud-agent",
      PresentBackgroundJobs.handleMessage,
      retryStepsFromConfig(TOPIC_NAME, appConfig.agent.messagingService.presentFlow)
    )
  } yield ()

  private def handleMessage(message: Message[UUID, WalletIdAndRecordId]): RIO[
    RESOURCES,
    Unit
  ] = {
    (for {
      _ <- ZIO.logDebug(s"!!! Present Proof Handling recordId: ${message.value} via Kafka queue")
      presentationService <- ZIO.service[PresentationService]
      walletAccessContext = WalletAccessContext(WalletId.fromUUID(message.value.walletId))
      record <- presentationService
        .findPresentationRecord(DidCommID(message.value.recordId.toString))
        .provide(ZLayer.succeed(walletAccessContext))
        .someOrElseZIO(ZIO.dieMessage("Record Not Found"))
      _ <- performPresentProofExchange(record)
        .tapSomeError { case f: Failure =>
          for {
            presentationService <- ZIO.service[PresentationService]
            _ <- presentationService
              .reportProcessingFailure(record.id, Some(f))
          } yield ()
        }
        .catchAll { e => ZIO.fail(RuntimeException(s"Attempt failed with: ${e}")) }
    } yield ()) @@ Metric
      .gauge("present_proof_flow_did_com_exchange_job_ms_gauge")
      .trackDurationWith(_.toMetricsSeconds)
  }

  private def counterMetric(key: String) = Metric
    .counterInt(key)
    .fromConst(1)

  private def performPresentProofExchange(record: PresentationRecord): ZIO[RESOURCES, ERROR, Unit] = {
    import org.hyperledger.identus.pollux.core.model.PresentationRecord.ProtocolState.*
    val exchange = for {
      _ <- ZIO.logDebug(s"Running action with records => $record")
      _ <- record match {
        case PresentationRecord(
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              InvitationGenerated,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.unit
        case PresentationRecord(_, _, _, _, _, _, _, InvitationExpired, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.unit
        case PresentationRecord(id, _, _, _, _, _, _, ProposalPending, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(id, _, _, _, _, _, _, ProposalSent, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(id, _, _, _, _, _, _, ProposalReceived, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(id, _, _, _, _, _, _, ProposalRejected, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              RequestPending,
              _,
              _,
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Verifier
          ZIO.fail(InvalidState("PresentationRecord 'RequestPending' with no Record"))
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              RequestPending,
              _,
              _,
              Some(requestPresentation),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Verifier
          Verifier.handleRequestPending(id, requestPresentation)
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              RequestSent,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Verifier
          ZIO.logDebug("PresentationRecord: RequestSent") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              RequestReceived,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          ZIO.logDebug("PresentationRecord: RequestReceived") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              RequestRejected,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          ZIO.logDebug("PresentationRecord: RequestRejected") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              ProblemReportPending,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(id, _, _, _, _, _, _, ProblemReportSent, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              ProblemReportReceived,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.fail(NotImplemented)
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationPending,
              _,
              _,
              None,
              _,
              _,
              credentialsToUse,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          ZIO.fail(InvalidState("PresentationRecord 'RequestPending' with no Record"))

        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationPending,
              credentialFormat,
              _,
              Some(requestPresentation),
              _,
              _,
              credentialsToUse,
              _,
              maybeCredentialsToUseJson,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          Prover.PresentationPending.handle(
            id,
            credentialsToUse,
            maybeCredentialsToUseJson,
            requestPresentation,
            credentialFormat
          )

        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationGenerated,
              _,
              _,
              _,
              _,
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          ZIO.fail(InvalidState("PresentationRecord in 'PresentationGenerated' with no Presentation"))

        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationGenerated,
              _,
              _,
              _,
              _,
              Some(presentation),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Prover
          ZIO.logDebug("PresentationRecord: PresentationGenerated") *> ZIO.unit
          Prover.handlePresentationGenerated(id, presentation)

        case PresentationRecord(id, _, _, _, _, _, _, PresentationSent, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) =>
          ZIO.logDebug("PresentationRecord: PresentationSent") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationReceived,
              _,
              _,
              _,
              _,
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Verifier
          ZIO.fail(InvalidState("PresentationRecord in 'PresentationReceived' with no Presentation"))
        case PresentationRecord(
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationReceived,
              _,
              _,
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.fail(InvalidState("PresentationRecord in 'PresentationReceived' with no Presentation Request"))
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationReceived,
              credentialFormat,
              invitation,
              Some(requestPresentation),
              _,
              Some(presentation),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) => // Verifier
          ZIO.logDebug("PresentationRecord: PresentationReceived") *> ZIO.unit
          Verifier.PresentationReceived.handle(id, requestPresentation, presentation, credentialFormat, invitation)

        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationVerificationFailed,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.logDebug("PresentationRecord: PresentationVerificationFailed") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationAccepted,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.logDebug("PresentationRecord: PresentationVerifiedAccepted") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationVerified,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.logDebug("PresentationRecord: PresentationVerified") *> ZIO.unit
        case PresentationRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              PresentationRejected,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          ZIO.logDebug("PresentationRecord: PresentationRejected") *> ZIO.unit
        case _ =>
          ZIO.logWarning(s"Unhandled PresentationRecord state: ${record.protocolState}")
      }
    } yield ()

    exchange
  }

  object Prover {
    object PresentationPending {
      private val ProverPresentationPendingToGeneratedSuccess = counterMetric(
        "present_proof_flow_prover_presentation_pending_to_generated_success_counter"
      )
      private val ProverPresentationPendingToGeneratedFailed = counterMetric(
        "present_proof_flow_prover_presentation_pending_to_generated_failed_counter"
      )
      private val ProverPresentationPendingToGenerated = counterMetric(
        "present_proof_flow_prover_presentation_pending_to_generated_all_counter"
      )

      private val metric =
        ProverPresentationPendingToGeneratedSuccess.trackSuccess
          @@ ProverPresentationPendingToGeneratedFailed.trackError
          @@ ProverPresentationPendingToGenerated
          @@ Metric
            .gauge("present_proof_flow_prover_presentation_pending_to_generated_flow_ms_gauge")
            .trackDurationWith(_.toMetricsSeconds)

      def handle(
          id: DidCommID,
          credentialsToUse: Option[List[String]],
          maybeCredentialsToUseJson: Option[AnoncredCredentialProofs],
          requestPresentation: RequestPresentation,
          credentialFormat: CredentialFormat
      ): ZIO[
        AppConfig & CredentialService & DIDService & COMMON_RESOURCES,
        ERROR,
        Unit
      ] = {
        val result = for {
          flags <- ZIO.service[AppConfig].map(_.featureFlag)
          ret <- credentialFormat match {
            case CredentialFormat.JWT =>
              flags.ifJWTIsEnabled(handle_JWT_VC(id, credentialsToUse, requestPresentation))
            case CredentialFormat.SDJWT =>
              flags.ifSDJWTIsEnabled(handle_SD_JWT_VC(id, credentialsToUse, requestPresentation))
            case CredentialFormat.AnonCreds =>
              flags.ifSDJWTIsEnabled(handleAnoncred(id, maybeCredentialsToUseJson, requestPresentation))
          }
        } yield ret
        result @@ metric
      }

      /** prover presentation pending to generated flow */
      private def handle_JWT_VC(
          id: DidCommID,
          credentialsToUse: Option[List[String]],
          requestPresentation: RequestPresentation
      ): ZIO[
        CredentialService & DIDService & COMMON_RESOURCES,
        ERROR,
        Unit
      ] = for {
        walletAccessContext <- ZIO
          .fromOption(requestPresentation.to)
          .flatMap(buildWalletAccessContextLayer)
          .mapError(_ => PresentationError.RequestPresentationMissingField(id.value, "recipient"))
        _ <- for {
          presentationService <- ZIO.service[PresentationService]
          prover <- createPrismDIDIssuerFromPresentationCredentials(id, credentialsToUse.getOrElse(Nil))
            .provideSomeLayer(ZLayer.succeed(walletAccessContext))
          presentation <-
            for {
              presentationPayload <-
                presentationService
                  .createJwtPresentationPayloadFromRecord(
                    id,
                    prover,
                    Instant.now()
                  )
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              signedJwtPresentation = JwtPresentation.toEncodedJwt(
                presentationPayload.toW3CPresentationPayload,
                prover
              )
              presentation <- createPresentation(id, requestPresentation, signedJwtPresentation)
            } yield presentation
          _ <- presentationService
            .markPresentationGenerated(id, presentation)
            .provideSomeLayer(ZLayer.succeed(walletAccessContext))
        } yield ()
      } yield ()

      private def createPresentation(
          id: DidCommID,
          requestPresentation: RequestPresentation,
          signedJwtPresentation: JWT
      ): ZIO[Any, PresentationError, Presentation] = {
        for {
          from <- ZIO
            .fromOption(requestPresentation.to)
            .mapError(_ => PresentationError.RequestPresentationMissingField(id.value, "recipient"))
          to <- ZIO
            .fromOption(requestPresentation.from)
            .mapError(_ => PresentationError.RequestPresentationMissingField(id.value, "sender"))
        } yield Presentation(
          body = Presentation.Body(
            goal_code = requestPresentation.body.goal_code,
            comment = requestPresentation.body.comment
          ),
          attachments = requestPresentation.attachments.map(attachment =>
            AttachmentDescriptor
              .buildBase64Attachment(
                payload = signedJwtPresentation.value.getBytes(),
                mediaType = attachment.media_type,
                format = attachment.format.map {
                  case PresentCredentialRequestFormat.JWT.name => PresentCredentialFormat.JWT.name
                  case format                                  =>
                    throw throw RuntimeException(
                      s"Unexpected PresentCredentialRequestFormat=$format. Expecting: ${PresentCredentialRequestFormat.JWT.name}"
                    )
                }
              )
          ),
          thid = requestPresentation.thid.orElse(Some(requestPresentation.id)),
          from = from,
          to = to
        )
      }

      private def handle_SD_JWT_VC(
          id: DidCommID,
          credentialsToUse: Option[List[String]],
          requestPresentation: RequestPresentation
      ): ZIO[
        CredentialService & DIDService & COMMON_RESOURCES,
        ERROR,
        Unit
      ] = for {
        walletAccessContext <- ZIO
          .fromOption(requestPresentation.to)
          .flatMap(buildWalletAccessContextLayer)
          .mapError(_ => PresentationError.RequestPresentationMissingField(id.value, "recipient"))
        result <-
          for {
            presentationService <- ZIO.service[PresentationService]
            maybeHolderPrivateKey <- findHolderPrivateKeyFromCredentials(id, credentialsToUse.getOrElse(Nil))
              .provideSomeLayer(ZLayer.succeed(walletAccessContext))
            presentation <-
              for {
                presentation <- presentationService
                  .createSDJwtPresentation(id, requestPresentation, maybeHolderPrivateKey)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              } yield presentation
            _ <- presentationService
              .markPresentationGenerated(id, presentation)
              .provideSomeLayer(ZLayer.succeed(walletAccessContext))
          } yield ()
      } yield result

      private def handleAnoncred(
          id: DidCommID,
          maybeCredentialsToUseJson: Option[AnoncredCredentialProofs],
          requestPresentation: RequestPresentation
      ): ZIO[
        PresentationService & DIDNonSecretStorage,
        Failure,
        Unit
      ] = {
        maybeCredentialsToUseJson match {
          case Some(credentialsToUseJson) =>
            val proverPresentationPendingToGeneratedFlow = for {
              walletAccessContext <- ZIO
                .fromOption(requestPresentation.to)
                .flatMap(buildWalletAccessContextLayer)
                .mapError(_ => PresentationError.RequestPresentationMissingField(id.value, "recipient"))
              result <- for {
                presentationService <- ZIO.service[PresentationService]
                anoncredCredentialProofs <-
                  AnoncredCredentialProofsV1.schemaSerDes
                    .deserialize(credentialsToUseJson)
                    .mapError(error => PresentationError.AnoncredCredentialProofParsingError(error.error))
                presentation <-
                  presentationService
                    .createAnoncredPresentation(
                      requestPresentation,
                      id,
                      anoncredCredentialProofs,
                      Instant.now()
                    )
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext))
                _ <- presentationService
                  .markPresentationGenerated(id, presentation)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              } yield ()
            } yield result
            proverPresentationPendingToGeneratedFlow
          case None =>
            ZIO.fail(InvalidState("AnonCreds PresentationRecord 'RequestPending' with no credentialsToUseJson Record"))
        }

      }

      private def createPrismDIDIssuerFromPresentationCredentials(
          presentationId: DidCommID,
          credentialsToUse: Seq[String]
      ): ZIO[
        CredentialService & DIDService & ManagedDIDService & DIDNonSecretStorage & WalletAccessContext,
        ERROR,
        JwtIssuer
      ] = {
        for {
          credentialService <- ZIO.service[CredentialService]
          // Choose first credential from the list to detect the subject DID to be used in Presentation.
          // Holder binding check implies that any credential record can be chosen to detect the DID to use in VP.
          credentialRecordId <- ZIO
            .fromOption(credentialsToUse.headOption)
            .mapError(_ => PresentationError.NoCredentialFoundInRecord(presentationId))
          credentialRecordUuid <- ZIO
            .attempt(DidCommID(credentialRecordId))
            .mapError(_ => PresentationError.NotValidDidCommID(credentialRecordId))
          issueCredentialRecord <- credentialService
            .findById(credentialRecordUuid)
            .someOrFail(CredentialServiceError.RecordNotFound(credentialRecordUuid))
          vcSubjectId <- issueCredentialRecord.subjectId match
            case None        => ZIO.dieMessage(s"VC SubjectId not found in credential record: $credentialRecordUuid")
            case Some(value) => ZIO.succeed(value)
          proverDID <- ZIO
            .fromEither(PrismDID.fromString(vcSubjectId))
            .mapError(e => CredentialServiceError.UnsupportedDidFormat(vcSubjectId))
          longFormPrismDID <- getLongForm(proverDID, true)
          mKidIssuer = issueCredentialRecord.keyId
          jwtIssuer <- createJwtVcIssuer(longFormPrismDID, VerificationRelationship.Authentication, mKidIssuer)
        } yield jwtIssuer
      }

      // Holder / Prover Get the Holder/Prover PrismDID from the IssuedCredential
      // SDJWT Only currrently When holder accepts offer he provides the subjectDid and optional keyId which is used for key binding
      private def findHolderPrivateKeyFromCredentials(
          presentationId: DidCommID,
          credentialsToUse: Seq[String]
      ): ZIO[
        CredentialService & DIDService & ManagedDIDService & DIDNonSecretStorage & WalletAccessContext,
        ERROR,
        Option[HolderPrivateKey]
      ] = {
        for {
          credentialService <- ZIO.service[CredentialService]
          // Choose first credential from the list to detect the subject DID to be used in Presentation.
          // Holder binding check implies that any credential record can be chosen to detect the DID to use in VP.
          credentialRecordId <- ZIO
            .fromOption(credentialsToUse.headOption)
            .mapError(_ => PresentationError.NoCredentialFoundInRecord(presentationId))
          credentialRecordUuid <- ZIO
            .attempt(DidCommID(credentialRecordId))
            .mapError(_ => PresentationError.NotValidDidCommID(credentialRecordId))
          credentialRecord <- credentialService
            .findById(credentialRecordUuid)
            .someOrFail(CredentialServiceError.RecordNotFound(credentialRecordUuid))
          vcSubjectId <- ZIO
            .fromOption(credentialRecord.subjectId)
            .orElseFail(CredentialServiceError.NoSubjectFoundInRecord(credentialRecordUuid))

          proverDID <- ZIO
            .fromEither(PrismDID.fromString(vcSubjectId))
            .mapError(e => CredentialServiceError.UnsupportedDidFormat(vcSubjectId))
          longFormPrismDID <- getLongForm(proverDID, true)

          optionalHolderPrivateKey <- credentialRecord.keyId match
            case Some(keyId) =>
              findHolderEd25519SigningKey(
                longFormPrismDID,
                VerificationRelationship.Authentication,
                keyId
              ).map(ed25519keyPair => Option(HolderPrivateKey(ed25519keyPair.privateKey)))
            case None => ZIO.succeed(None)

        } yield optionalHolderPrivateKey
      }
    }

    def handlePresentationGenerated(id: DidCommID, presentation: Presentation): ZIO[
      JwtDidResolver & COMMON_RESOURCES & MESSAGING_RESOURCES,
      Failure,
      Unit
    ] = {

      val ProverSendPresentationMsgSuccess = counterMetric(
        "present_proof_flow_prover_send_presentation_msg_success_counter"
      )
      val ProverSendPresentationMsgFailed = counterMetric(
        "present_proof_flow_prover_send_presentation_msg_failed_counter"
      )

      val ProverPresentationGeneratedToSentSuccess = counterMetric(
        "present_proof_flow_prover_presentation_generated_to_sent_success_counter"
      )
      val ProverPresentationGeneratedToSentFailed = counterMetric(
        "present_proof_flow_prover_presentation_generated_to_sent_failed_counter"
      )
      val ProverPresentationGeneratedToSent = counterMetric(
        "present_proof_flow_prover_presentation_generated_to_sent_all_counter"
      )

      val metric = ProverPresentationGeneratedToSentSuccess.trackSuccess
        @@ ProverPresentationGeneratedToSentFailed.trackError
        @@ ProverPresentationGeneratedToSent
        @@ Metric
          .gauge("present_proof_flow_prover_presentation_generated_to_sent_flow_ms_gauge")
          .trackDurationWith(_.toMetricsSeconds)

      val ProverPresentationGeneratedToSentFlow = for {
        _ <- ZIO.log(s"PresentationRecord: PresentationPending (Send Message)")
        walletAccessContext <- buildWalletAccessContextLayer(presentation.from)
        result <- for {
          didCommAgent <- buildDIDCommAgent(presentation.from).provideSomeLayer(ZLayer.succeed(walletAccessContext))
          resp <-
            MessagingService
              .send(presentation.makeMessage)
              .provideSomeLayer(didCommAgent)
              @@ Metric
                .gauge("present_proof_flow_prover_send_presentation_msg_ms_gauge")
                .trackDurationWith(_.toMetricsSeconds)
          service <- ZIO.service[PresentationService]
          _ <- {
            if (resp.status >= 200 && resp.status < 300)
              service
                .markPresentationSent(id)
                .provideSomeLayer(
                  ZLayer.succeed(walletAccessContext)
                ) @@ ProverSendPresentationMsgSuccess @@ CustomMetricsAspect
                .endRecordingTime(
                  s"${id}_present_proof_flow_prover_presentation_generated_to_sent_ms_gauge",
                  "present_proof_flow_prover_presentation_generated_to_sent_ms_gauge"
                )
            else ZIO.fail(ErrorResponseReceivedFromPeerAgent(resp)) @@ ProverSendPresentationMsgFailed
          }
        } yield ()

      } yield result

      ProverPresentationGeneratedToSentFlow
        @@ metric
    }
  }

  object Verifier {

    def handleRequestPending(id: DidCommID, requestPresentation: RequestPresentation): ZIO[
      JwtDidResolver & COMMON_RESOURCES & MESSAGING_RESOURCES,
      Failure,
      Unit
    ] = {
      val VerifierSendPresentationRequestMsgSuccess = counterMetric(
        "present_proof_flow_verifier_send_presentation_request_msg_success_counter"
      )
      val VerifierSendPresentationRequestMsgFailed = counterMetric(
        "present_proof_flow_verifier_send_presentation_request_msg_failed_counter"
      )

      val VerifierReqPendingToSentSuccess = counterMetric(
        "present_proof_flow_verifier_request_pending_to_sent_success_counter"
      )
      val VerifierReqPendingToSentFailed = counterMetric(
        "present_proof_flow_verifier_request_pending_to_sent_failed_counter"
      )
      val VerifierReqPendingToSent = counterMetric(
        "present_proof_flow_verifier_request_pending_to_sent_all_counter"
      )
      val metric =
        VerifierReqPendingToSentSuccess.trackSuccess
          @@ VerifierReqPendingToSentFailed.trackError
          @@ VerifierReqPendingToSent
          @@ Metric
            .gauge("present_proof_flow_verifier_req_pending_to_sent_flow_ms_gauge")
            .trackDurationWith(_.toMetricsSeconds)

      val verifierReqPendingToSentFlow = for {
        _ <- ZIO.log(s"PresentationRecord: RequestPending (Send Message)")
        walletAccessContext <- ZIO
          .fromOption(requestPresentation.from)
          .mapError(_ => RequestPresentationMissingField(id.value, "sender"))
          .flatMap(buildWalletAccessContextLayer)

        result <- for {
          didOps <- ZIO.service[DidOps]
          didCommAgent <- ZIO
            .fromOption(requestPresentation.from)
            .mapError(_ => RequestPresentationMissingField(id.value, "sender"))
            .flatMap(buildDIDCommAgent(_).provideSomeLayer(ZLayer.succeed(walletAccessContext)))
          resp <-
            MessagingService
              .send(requestPresentation.makeMessage)
              .provideSomeLayer(didCommAgent)
              @@ Metric
                .gauge("present_proof_flow_verifier_send_presentation_request_msg_ms_gauge")
                .trackDurationWith(_.toMetricsSeconds)
          service <- ZIO.service[PresentationService]
          _ <- {
            if (resp.status >= 200 && resp.status < 300)
              service
                .markRequestPresentationSent(
                  id
                )
                .provideSomeLayer(
                  ZLayer.succeed(walletAccessContext)
                ) @@ VerifierSendPresentationRequestMsgSuccess @@ CustomMetricsAspect.endRecordingTime(
                s"${id}_present_proof_flow_verifier_req_pending_to_sent_ms_gauge",
                "present_proof_flow_verifier_req_pending_to_sent_ms_gauge"
              )
            else ZIO.fail(ErrorResponseReceivedFromPeerAgent(resp)) @@ VerifierSendPresentationRequestMsgFailed
          }
        } yield ()
      } yield result
      verifierReqPendingToSentFlow
        @@ metric
    }

    object PresentationReceived {
      private val VerifierPresentationReceivedToProcessedSuccess = counterMetric(
        "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_success_counter"
      )
      private val VerifierPresentationReceivedToProcessedFailed = counterMetric(
        "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_failed_counter"
      )
      private val VerifierPresentationReceivedToProcessed = counterMetric(
        "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_all_counter"
      )

      private val metric = VerifierPresentationReceivedToProcessedSuccess.trackSuccess
        @@ VerifierPresentationReceivedToProcessedFailed.trackError
        @@ VerifierPresentationReceivedToProcessed
        @@ Metric
          .gauge(
            "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_flow_ms_gauge"
          )
          .trackDurationWith(_.toMetricsSeconds)

      def handle(
          id: DidCommID,
          requestPresentation: RequestPresentation,
          presentation: Presentation,
          credentialFormat: CredentialFormat,
          invitation: Option[Invitation]
      ): ZIO[
        AppConfig & JwtDidResolver & UriResolver & COMMON_RESOURCES & MESSAGING_RESOURCES,
        Failure,
        Unit
      ] = {
        val result = for {
          flags <- ZIO.service[AppConfig].map(_.featureFlag)
          ret <- credentialFormat match {
            case CredentialFormat.JWT =>
              flags.ifJWTIsEnabled(handleJWT(id, requestPresentation, presentation, invitation))
            case CredentialFormat.SDJWT =>
              flags.ifSDJWTIsEnabled(handleSDJWT(id, presentation, invitation))
            case CredentialFormat.AnonCreds =>
              flags.ifAnoncredIsEnabled(handleAnoncred(id, requestPresentation, presentation, invitation))
          }
        } yield ret
        result @@ metric
      }

      private def checkInvitationExpiry(
          id: DidCommID,
          invitation: Option[Invitation]
      ): ZIO[PresentationService & WalletAccessContext, PresentationError, Unit] = {
        invitation.flatMap(_.expires_time) match {
          case Some(expiryTime) if Instant.now().getEpochSecond > expiryTime =>
            for {
              service <- ZIO.service[PresentationService]
              _ <- service.markPresentationInvitationExpired(id)
              _ <- ZIO.fail(PresentationError.InvitationExpired(s"Invitation has expired. Expiry time: $expiryTime"))
            } yield ()
          case _ => ZIO.unit
        }
      }

      private def buildReportProblem(presentation: Presentation, error: String): ReportProblem = {
        ReportProblem.build(
          fromDID = presentation.to,
          toDID = presentation.from,
          pthid = presentation.thid.getOrElse(presentation.id),
          code = ProblemCode("e.p.presentation-verification-failed"),
          comment = Some(error)
        )
      }

      private def handleJWT(
          id: DidCommID,
          requestPresentation: RequestPresentation,
          presentation: Presentation,
          invitation: Option[Invitation]
      ): ZIO[
        AppConfig & JwtDidResolver & UriResolver & COMMON_RESOURCES & MESSAGING_RESOURCES,
        Failure,
        Unit
      ] = {
        val clock = java.time.Clock.system(ZoneId.systemDefault)
        for {
          walletAccessContext <- buildWalletAccessContextLayer(presentation.to)
          _ <- checkInvitationExpiry(id, invitation).provideSomeLayer(ZLayer.succeed(walletAccessContext))
          result <- for {
            didResolverService <- ZIO.service[JwtDidResolver]
            claimsValidationResult <- presentation.attachments.head.data match {
              case Base64(data) =>
                val base64Decoded = new String(java.util.Base64.getUrlDecoder.decode(data))
                val maybePresentationOptions: Either[PresentationError, Option[Options]] =
                  requestPresentation.attachments.headOption
                    .map(attachment =>
                      attachment.data.toJson
                        .fromJson[JsonData]
                        .leftMap(err => PresentationDecodingError(s"JsonData decoding error: $err"))
                        .flatMap(data =>
                          org.hyperledger.identus.pollux.core.model.presentation.PresentationAttachment.given_JsonDecoder_PresentationAttachment
                            .decodeJson(data.json.toJson)
                            .map(_.options)
                            .leftMap(err => PresentationDecodingError(s"PresentationAttachment decoding error: $err"))
                        )
                    )
                    .getOrElse(Right(None))
                val schemaIdAndTrustedIssuers = requestPresentation.body.proof_types.map { proofType =>
                  CredentialSchemaAndTrustedIssuersConstraint(
                    proofType.schema,
                    proofType.trustIssuers.map(_.map(_.value))
                  )
                }

                val presentationClaimsValidationResult = for {
                  validationResult: Validation[String, Unit] <- ZIO.fromEither(maybePresentationOptions.map {
                    case Some(options) =>
                      JwtPresentation
                        .validatePresentation(
                          JWT(base64Decoded),
                          Some(options.domain),
                          Some(options.challenge),
                          schemaIdAndTrustedIssuers
                        )
                    case _ =>
                      JwtPresentation
                        .validatePresentation(
                          JWT(base64Decoded),
                          None,
                          None,
                          schemaIdAndTrustedIssuers
                        )
                  })

                  verificationConfig <- ZIO.service[AppConfig].map(_.agent.verification)
                  _ <- ZIO.log(s"VerificationConfig: ${verificationConfig}")

                  // https://www.w3.org/TR/vc-data-model/#proofs-signatures-0
                  // A proof is typically attached to a verifiable presentation for authentication purposes
                  // and to a verifiable credential as a method of assertion.
                  uriResolver <- ZIO.service[UriResolver]
                  result: Validation[String, Unit] <- JwtPresentation
                    .verify(
                      JWT(base64Decoded),
                      verificationConfig.toPresentationVerificationOptions()
                    )(didResolverService, uriResolver)(clock)
                    .mapError(error => PresentationError.PresentationVerificationError(error.mkString))

                } yield Seq(validationResult, result)
                presentationClaimsValidationResult

              case any => ZIO.fail(PresentationReceivedError("Only Base64 Supported"))
            }
            credentialsClaimsValidationResult = ZValidation
              .validateAll(claimsValidationResult)
              .map(_ => ())
            _ <- credentialsClaimsValidationResult match
              case l @ ZFailure(_, _) => ZIO.logError(s"CredentialsClaimsValidationResult: $l")
              case l @ Success(_, _)  => ZIO.logInfo(s"CredentialsClaimsValidationResult: $l")
            service <- ZIO.service[PresentationService]
            presReceivedToProcessedAspect = CustomMetricsAspect.endRecordingTime(
              s"${id}_present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge",
              "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge"
            )
            _ <- credentialsClaimsValidationResult match {
              case Success(log, value) =>
                service
                  .markPresentationVerified(id)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ presReceivedToProcessedAspect
              case ZFailure(log, error) =>
                for {
                  _ <- service
                    .markPresentationVerificationFailed(id)
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ presReceivedToProcessedAspect
                  didCommAgent <- buildDIDCommAgent(presentation.to).provideSomeLayer(
                    ZLayer.succeed(walletAccessContext)
                  )
                  reportProblem = buildReportProblem(presentation, error.mkString)
                  _ <-
                    MessagingService
                      .send(reportProblem.toMessage)
                      .provideSomeLayer(didCommAgent)
                  _ <- ZIO.log(s"CredentialsClaimsValidationResult: $error")
                } yield ()
            }

          } yield ()
        } yield result
      }

      private def handleSDJWT(id: DidCommID, presentation: Presentation, invitation: Option[Invitation]): ZIO[
        JwtDidResolver & COMMON_RESOURCES & MESSAGING_RESOURCES,
        Failure,
        Unit
      ] = {
        for {
          walletAccessContext <- buildWalletAccessContextLayer(presentation.to)
          _ <- checkInvitationExpiry(id, invitation).provideSomeLayer(ZLayer.succeed(walletAccessContext))
          result <- for {
            didResolverService <- ZIO.service[JwtDidResolver]
            credentialsClaimsValidationResult <- presentation.attachments.head.data match {
              case Base64(data) =>
                val base64Decoded = new String(java.util.Base64.getUrlDecoder.decode(data))
                val verifiedClaims = for {
                  presentation <- ZIO.succeed(PresentationCompact.unsafeFromCompact(base64Decoded))
                  iss <- ZIO.fromEither(presentation.iss)
                  ed25519PublicKey <- resolveToEd25519PublicKey(iss)
                  ret = SDJWT.getVerifiedClaims(
                    IssuerPublicKey(ed25519PublicKey),
                    presentation
                  )
                  _ <- ZIO.logInfo(s"ClaimsValidationResult: $ret")
                } yield ret
                verifiedClaims.mapError(error => PresentationReceivedError(error.toString))
              case any => ZIO.fail(PresentationReceivedError("Only Base64 Supported"))
            }
            service <- ZIO.service[PresentationService]
            _ <- credentialsClaimsValidationResult match
              case valid: SDJWT.Valid =>
                ZIO.logInfo(s"CredentialsClaimsValidationResult: $valid")
                val jsonObj = valid.asInstanceOf[SDJWT.ValidClaims].claims
                service
                  .updateWithSDJWTDisclosedClaims(id, jsonObj)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              case invalid: SDJWT.Invalid =>
                ZIO.logError(s"CredentialsClaimsValidationResult: $invalid")
            presReceivedToProcessedAspect = CustomMetricsAspect.endRecordingTime(
              s"${id}_present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge",
              "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge"
            )
            _ <- credentialsClaimsValidationResult match
              case valid: SDJWT.Valid =>
                service
                  .markPresentationVerified(id)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ presReceivedToProcessedAspect
              case invalid: SDJWT.Invalid =>
                for {
                  _ <- service
                    .markPresentationVerificationFailed(id)
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ presReceivedToProcessedAspect
                  didCommAgent <- buildDIDCommAgent(presentation.to).provideSomeLayer(
                    ZLayer.succeed(walletAccessContext)
                  )
                  reportProblem = buildReportProblem(presentation, invalid.toString)
                  resp <-
                    MessagingService
                      .send(reportProblem.toMessage)
                      .provideSomeLayer(didCommAgent)
                  _ <- ZIO.log(s"CredentialsClaimsValidationResult: ${invalid.toString}")
                } yield ()
          } yield ()
        } yield result
      }

      private def handleAnoncred(
          id: DidCommID,
          requestPresentation: RequestPresentation,
          presentation: Presentation,
          invitation: Option[Invitation]
      ): ZIO[
        PresentationService & DIDNonSecretStorage & MESSAGING_RESOURCES,
        PresentationError | DIDSecretStorageError,
        Unit
      ] = {
        for {
          walletAccessContext <- buildWalletAccessContextLayer(presentation.to)
          _ <- checkInvitationExpiry(id, invitation).provideSomeLayer(ZLayer.succeed(walletAccessContext))
          result <- for {
            service <- ZIO.service[PresentationService]
            presReceivedToProcessedAspect = CustomMetricsAspect.endRecordingTime(
              s"${id}_present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge",
              "present_proof_flow_verifier_presentation_received_to_verification_success_or_failure_ms_gauge"
            )
            _ <- (service
              .verifyAnoncredPresentation(presentation, requestPresentation, id)
              .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ presReceivedToProcessedAspect)
              .flatMapError(e =>
                for {
                  didCommAgent <- buildDIDCommAgent(presentation.to).provideSomeLayer(
                    ZLayer.succeed(walletAccessContext)
                  )
                  reportProblem = buildReportProblem(presentation, e.toString)
                  _ <-
                    MessagingService
                      .send(reportProblem.toMessage)
                      .provideSomeLayer(didCommAgent)
                  _ <- ZIO.log(s"CredentialsClaimsValidationResult: ${e.toString}")
                } yield ()
                ZIO.succeed(e)
              )
          } yield ()
        } yield result
      }
    }
  }
}
