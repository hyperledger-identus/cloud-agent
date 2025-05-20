package org.hyperledger.identus.agent.server.jobs

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.jobs.BackgroundJobError.ErrorResponseReceivedFromPeerAgent
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.mercury.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError
import org.hyperledger.identus.pollux.core.service.CredentialService
import org.hyperledger.identus.resolvers.DIDResolver
import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.{Message, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.shared.utils.aspects.CustomMetricsAspect
import org.hyperledger.identus.shared.utils.DurationOps.toMetricsSeconds
import zio.*
import zio.metrics.*

import java.util.UUID

object IssueBackgroundJobs extends BackgroundJobsHelper {

  private val TOPIC_NAME = "issue"

  val issueFlowsHandler = for {
    appConfig <- ZIO.service[AppConfig]
    _ <- messaging.MessagingService.consumeWithRetryStrategy(
      "identus-cloud-agent",
      IssueBackgroundJobs.handleMessage,
      retryStepsFromConfig(TOPIC_NAME, appConfig.agent.messagingService.issueFlow)
    )
  } yield ()

  private def handleMessage(message: Message[UUID, WalletIdAndRecordId]): RIO[
    HttpClient & DidOps & DIDResolver & (CredentialService & DIDNonSecretStorage & (ManagedDIDService & AppConfig)),
    Unit
  ] = {
    (for {
      _ <- ZIO.logDebug(s"!!! Handling recordId: ${message.value} via Kafka queue")
      credentialService <- ZIO.service[CredentialService]
      walletAccessContext = WalletAccessContext(WalletId.fromUUID(message.value.walletId))
      record <- credentialService
        .findById(DidCommID(message.value.recordId.toString))
        .provideSome(ZLayer.succeed(walletAccessContext))
        .someOrElseZIO(ZIO.dieMessage(s"Record Not Found: ${message.value.recordId}"))
      _ <- performIssueCredentialExchange(record)
        .tapSomeError { case (walletAccessContext, errorResponse) =>
          for {
            credentialService <- ZIO.service[CredentialService]
            _ <- credentialService
              .reportProcessingFailure(record.id, Some(errorResponse))
              .provideSomeLayer(ZLayer.succeed(walletAccessContext))
          } yield ()
        }
        .catchAll { e => ZIO.fail(RuntimeException(s"Attempt failed with: ${e}")) }
    } yield ()) @@ Metric
      .gauge("issuance_flow_did_com_exchange_job_ms_gauge")
      .trackDurationWith(_.toMetricsSeconds)
  }

  private def counterMetric(key: String) = Metric
    .counterInt(key)
    .fromConst(1)

  private def performIssueCredentialExchange(record: IssueCredentialRecord) = {
    import IssueCredentialRecord.*
    import IssueCredentialRecord.ProtocolState.*

    val IssuerSendOfferMsgFailed = counterMetric(
      "issuance_flow_issuer_send_offer_msg_failed_counter"
    )
    val IssuerSendOfferMsgSucceed = counterMetric(
      "issuance_flow_issuer_send_offer_msg_succeed_counter"
    )

    val IssuerSendOfferSucceed = counterMetric(
      "issuance_flow_issuer_send_offer_flow_succeed_counter"
    )

    val IssuerSendOfferFailed = counterMetric(
      "issuance_flow_issuer_send_offer_flow_failed_counter"
    )

    val IssuerSendOfferAll = counterMetric(
      "issuance_flow_issuer_send_offer_flow_all_counter"
    )

    val HolderPendingToGeneratedSuccess = counterMetric(
      "issuance_flow_holder_req_pending_to_generated_flow_success_counter"
    )

    val HolderPendingToGeneratedFailed = counterMetric(
      "issuance_flow_holder_req_pending_to_generated_flow_failed_counter"
    )

    val HolderPendingToGeneratedAll = counterMetric(
      "issuance_flow_holder_req_pending_to_generated_flow_all_counter"
    )

    val HolderSendReqSucceed = counterMetric(
      "issuance_flow_holder_send_req_msg_succeed_counter"
    )

    val HolderSendReqFailed = counterMetric(
      "issuance_flow_holder_send_req_msg_failed_counter"
    )

    val HolderGeneratedToSentSucceed = counterMetric(
      "issuance_flow_holder_req_generated_to_sent_flow_success_counter"
    )
    val HolderGeneratedToSentFailed = counterMetric(
      "issuance_flow_holder_req_generated_to_sent_flow_failed_counter"
    )
    val HolderGeneratedToSentAll = counterMetric(
      "issuance_flow_holder_req_generated_to_sent_flow_all_counter"
    )

    val IssuerReceivedToPendingSuccess = counterMetric(
      "issuance_flow_issuer_cred_received_to_pending_flow_success_counter"
    )

    val IssuerReceivedToPendingFailed = counterMetric(
      "issuance_flow_issuer_cred_received_to_pending_flow_failed_counter"
    )

    val IssuerReceivedToPendingAll = counterMetric(
      "issuance_flow_issuer_cred_received_to_pending_flow_all_counter"
    )

    val IssuerPendingToGeneratedSuccess = counterMetric(
      "issuance_flow_issuer_cred_pending_to_generated_flow_success_counter"
    )
    val IssuerPendingToGeneratedFailed = counterMetric(
      "issuance_flow_issuer_cred_pending_to_generated_flow_failed_counter"
    )
    val IssuerPendingToGeneratedAll = counterMetric(
      "issuance_flow_issuer_cred_pending_to_generated_flow_all_counter"
    )

    val IssuerSendCredentialSuccess = counterMetric(
      "issuance_flow_issuer_send_cred_success_counter"
    )

    val IssuerSendCredentialFailed = counterMetric(
      "issuance_flow_issuer_send_cred_failed_counter"
    )

    val IssuerSendCredentialAll = counterMetric(
      "issuance_flow_issuer_send_cred_all_counter"
    )

    val IssuerSendCredentialMsgFailed = counterMetric(
      "issuance_flow_issuer_send_credential_msg_failed_counter"
    )
    val IssuerSendCredentialMsgSuccess = counterMetric(
      "issuance_flow_issuer_send_credential_msg_succeed_counter"
    )

    val exchange = for {
      _ <- ZIO.logDebug(s"Running action with records => $record")
      _ <- record match {
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              InvitationGenerated,
              Some(offer),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
            ) =>
          ZIO.logDebug(s" Connectionless InvitationGenerated record received no processing required") *> ZIO.unit
        // Offer should be sent from Issuer to Holder
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              OfferPending,
              Some(offer),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
            ) =>
          val sendOfferFlow = for {
            _ <- ZIO.log(s"IssueCredentialRecord: OfferPending (START)")
            walletAccessContext <- buildWalletAccessContextLayer(offer.from)
            result <- (for {
              didCommAgent <- buildDIDCommAgent(offer.from).provideSomeLayer(ZLayer.succeed(walletAccessContext))
              resp <- MessagingService
                .send(offer.makeMessage)
                .provideSomeLayer(didCommAgent) @@ Metric
                .gauge("issuance_flow_issuer_send_offer_ms_gauge")
                .trackDurationWith(_.toMetricsSeconds)
              credentialService <- ZIO.service[CredentialService]
              _ <- {
                if (resp.status >= 200 && resp.status < 300)
                  credentialService.markOfferSent(id).provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@
                    IssuerSendOfferMsgSucceed @@
                    CustomMetricsAspect.endRecordingTime(
                      s"${record.id}_issuer_offer_pending_to_sent_ms_gauge",
                      "issuance_flow_issuer_offer_pending_to_sent_ms_gauge"
                    )
                else ZIO.fail(ErrorResponseReceivedFromPeerAgent(resp)) @@ IssuerSendOfferMsgFailed
              }
            } yield ()).mapError(e => (walletAccessContext, e))
          } yield result

          sendOfferFlow @@ IssuerSendOfferSucceed.trackSuccess
            @@ IssuerSendOfferFailed.trackError
            @@ IssuerSendOfferAll
            @@ Metric
              .gauge("issuance_flow_issuer_send_offer_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // Request should be sent from Holder to Issuer
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.JWT,
              _,
              Role.Holder,
              Some(subjectId),
              _,
              _,
              _,
              RequestPending,
              Some(offer),
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          val holderPendingToGeneratedFlow = for {
            flags <- ZIO.service[AppConfig].map(_.featureFlag)
            ret <- flags.ifJWTIsEnabled(
              for {
                walletAccessContext <- ZIO
                  .fromOption(offer.to)
                  .mapError(_ => CredentialServiceError.CredentialOfferMissingField(id.value, "recipient"))
                  .flatMap(buildWalletAccessContextLayer)
                credentialService <- ZIO.service[CredentialService]
                _ <- credentialService
                  .generateJWTCredentialRequest(id)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              } yield ()
            )
          } yield ret

          holderPendingToGeneratedFlow @@ HolderPendingToGeneratedSuccess.trackSuccess
            @@ HolderPendingToGeneratedFailed.trackError
            @@ HolderPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_holder_req_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.SDJWT,
              _,
              Role.Holder,
              Some(subjectId),
              keyId,
              _,
              _,
              RequestPending,
              Some(offer),
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          val holderPendingToGeneratedFlow = for {
            flags <- ZIO.service[AppConfig].map(_.featureFlag)
            ret <- flags.ifSDJWTIsEnabled(
              for {
                walletAccessContext <- ZIO
                  .fromOption(offer.to)
                  .mapError(_ => CredentialServiceError.CredentialOfferMissingField(id.value, "recipient"))
                  .flatMap(buildWalletAccessContextLayer)
                credentialService <- ZIO.service[CredentialService]
                _ <- credentialService
                  .generateSDJWTCredentialRequest(id)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              } yield ()
            )
          } yield ret
          holderPendingToGeneratedFlow @@ HolderPendingToGeneratedSuccess.trackSuccess
            @@ HolderPendingToGeneratedFailed.trackError
            @@ HolderPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_holder_req_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.AnonCreds,
              _,
              Role.Holder,
              None,
              _,
              _,
              _,
              RequestPending,
              Some(offer),
              None,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          val holderPendingToGeneratedFlow = for {
            flags <- ZIO.service[AppConfig].map(_.featureFlag)
            ret <- flags.ifAnoncredIsEnabled(
              for {
                walletAccessContext <- ZIO
                  .fromOption(offer.to)
                  .mapError(_ => CredentialServiceError.CredentialOfferMissingField(id.value, "recipient"))
                  .flatMap(buildWalletAccessContextLayer)
                credentialService <- ZIO.service[CredentialService]
                _ <- credentialService
                  .generateAnonCredsCredentialRequest(id)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
              } yield ()
            )
          } yield ret
          holderPendingToGeneratedFlow @@ HolderPendingToGeneratedSuccess.trackSuccess
            @@ HolderPendingToGeneratedFailed.trackError
            @@ HolderPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_holder_req_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // Request should be sent from Holder to Issuer
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              Role.Holder,
              _,
              _,
              _,
              _,
              RequestGenerated,
              _,
              Some(request),
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) =>
          val holderGeneratedToSentFlow = for {
            walletAccessContext <- buildWalletAccessContextLayer(request.from)
            result <- (for {
              didCommAgent <- buildDIDCommAgent(request.from).provideSomeLayer(ZLayer.succeed(walletAccessContext))
              resp <- MessagingService
                .send(request.makeMessage)
                .provideSomeLayer(didCommAgent) @@ Metric
                .gauge("issuance_flow_holder_send_request_ms_gauge")
                .trackDurationWith(_.toMetricsSeconds)
              credentialService <- ZIO.service[CredentialService]
              _ <- {
                if (resp.status >= 200 && resp.status < 300)
                  credentialService
                    .markRequestSent(id)
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ HolderSendReqSucceed
                    @@ CustomMetricsAspect.endRecordingTime(
                      s"${record.id}_issuance_flow_holder_req_generated_to_sent",
                      "issuance_flow_holder_req_generated_to_sent_ms_gauge"
                    )
                else ZIO.fail(ErrorResponseReceivedFromPeerAgent(resp)) @@ HolderSendReqFailed
              }
            } yield ()).mapError(e => (walletAccessContext, e))
          } yield result

          holderGeneratedToSentFlow @@ HolderGeneratedToSentSucceed.trackSuccess
            @@ HolderGeneratedToSentFailed.trackError
            @@ HolderGeneratedToSentAll
            @@ Metric
              .gauge("issuance_flow_holder_req_generated_to_sent_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // 'automaticIssuance' is TRUE. Issuer automatically accepts the Request
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              invitation,
              Role.Issuer,
              _,
              _,
              _,
              Some(true),
              RequestReceived,
              _,
              Some(request),
              _,
              _,
              _,
              _,
              _,
              _,
              _,
            ) =>
          val issuerReceivedToPendingFlow = for {
            walletAccessContext <- buildWalletAccessContextLayer(request.to)
            result <- (for {
              credentialService <- ZIO.service[CredentialService]
              _ <- checkInvitationExpiry(id, invitation).provideSomeLayer(ZLayer.succeed(walletAccessContext))
              _ <- credentialService.acceptCredentialRequest(id).provideSomeLayer(ZLayer.succeed(walletAccessContext))
            } yield ()).mapError(e => (walletAccessContext, e))
          } yield result

          issuerReceivedToPendingFlow @@ IssuerReceivedToPendingSuccess.trackSuccess
            @@ IssuerReceivedToPendingFailed.trackError
            @@ IssuerReceivedToPendingAll
            @@ Metric
              .gauge("issuance_flow_issuer_cred_received_to_pending_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // Credential is pending, can be generated by Issuer
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.JWT,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              CredentialPending,
              _,
              _,
              _,
              Some(issue),
              _,
              Some(issuerDID),
              _,
              _,
              _,
            ) =>
          // Generate the JWT Credential and store it in DB as an attachment to IssueCredentialData
          // Set ProtocolState to CredentialGenerated
          // TODO Move all logic to service
          val issuerPendingToGeneratedFlow = for {
            config <- ZIO.service[AppConfig]
            ret <- config.featureFlag.ifJWTIsEnabled(
              for {
                walletAccessContext <- buildWalletAccessContextLayer(issue.from)
                credentialService <- ZIO.service[CredentialService]
                _ <- credentialService
                  .generateJWTCredential(id, config.pollux.statusListRegistry.publicEndpointUrl.toExternalForm)
                  .provideSomeLayer(ZLayer.succeed(walletAccessContext))
                  .mapError(e => (walletAccessContext, e))
              } yield ()
            )
          } yield ret

          issuerPendingToGeneratedFlow @@ IssuerPendingToGeneratedSuccess.trackSuccess
            @@ IssuerPendingToGeneratedFailed.trackError
            @@ IssuerPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_issuer_cred_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // Credential is pending, can be generated by Issuer
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.SDJWT,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              CredentialPending,
              _,
              _,
              _,
              Some(issue),
              _,
              Some(issuerDID),
              _,
              _,
              _,
            ) =>
          // Generate the JWT Credential and store it in DB as an attachment to IssueCredentialData
          // Set ProtocolState to CredentialGenerated
          // TODO Move all logic to service
          val issuerPendingToGeneratedFlow = for {
            config <- ZIO.service[AppConfig]
            ret <- config.featureFlag.ifSDJWTIsEnabled(
              for {
                walletAccessContext <- buildWalletAccessContextLayer(issue.from)
                result <- (for {
                  credentialService <- ZIO.service[CredentialService]
                  config <- ZIO.service[AppConfig]
                  _ <- credentialService
                    .generateSDJWTCredential(id, config.pollux.credentialSdJwtExpirationTime)
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext))
                } yield ()).mapError(e => (walletAccessContext, e))
              } yield result
            )
          } yield ret

          issuerPendingToGeneratedFlow @@ IssuerPendingToGeneratedSuccess.trackSuccess
            @@ IssuerPendingToGeneratedFailed.trackError
            @@ IssuerPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_issuer_cred_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              CredentialFormat.AnonCreds,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              CredentialPending,
              _,
              _,
              _,
              Some(issue),
              _,
              _,
              _,
              _,
              _,
            ) =>
          val issuerPendingToGeneratedFlow = for {
            config <- ZIO.service[AppConfig]
            ret <-
              config.featureFlag.ifAnoncredIsEnabled(
                for {
                  walletAccessContext <- buildWalletAccessContextLayer(issue.from)
                  result <- (for {
                    credentialService <- ZIO.service[CredentialService]
                    _ <- credentialService
                      .generateAnonCredsCredential(id)
                      .provideSomeLayer(ZLayer.succeed(walletAccessContext))
                  } yield ()).mapError(e => (walletAccessContext, e))
                } yield result
              )
          } yield ret

          issuerPendingToGeneratedFlow @@ IssuerPendingToGeneratedSuccess.trackSuccess
            @@ IssuerPendingToGeneratedFailed.trackError
            @@ IssuerPendingToGeneratedAll
            @@ Metric
              .gauge("issuance_flow_issuer_cred_pending_to_generated_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)

        // Credential has been generated and can be sent directly to the Holder
        case IssueCredentialRecord(
              id,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              Role.Issuer,
              _,
              _,
              _,
              _,
              CredentialGenerated,
              _,
              _,
              _,
              Some(issue),
              _,
              _,
              _,
              _,
              _,
            ) =>
          val sendCredentialManualIssuanceFlow = for {
            walletAccessContext <- buildWalletAccessContextLayer(issue.from)
            result <- (for {
              didCommAgent <- buildDIDCommAgent(issue.from).provideSomeLayer(ZLayer.succeed(walletAccessContext))
              resp <- MessagingService
                .send(issue.makeMessage)
                .provideSomeLayer(didCommAgent) @@ Metric
                .gauge("issuance_flow_issuer_send_credential_msg_ms_gauge")
                .trackDurationWith(_.toMetricsSeconds)
              credentialService <- ZIO.service[CredentialService]
              _ <- {
                if (resp.status >= 200 && resp.status < 300)
                  credentialService
                    .markCredentialSent(id)
                    .provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ IssuerSendCredentialMsgSuccess
                else ZIO.fail(ErrorResponseReceivedFromPeerAgent(resp)) @@ IssuerSendCredentialMsgFailed
              }
            } yield ()).mapError(e => (walletAccessContext, e))
          } yield result

          sendCredentialManualIssuanceFlow @@ IssuerSendCredentialSuccess.trackSuccess
            @@ IssuerSendCredentialFailed.trackError
            @@ IssuerSendCredentialAll @@ Metric
              .gauge("issuance_flow_issuer_send_cred_flow_ms_gauge")
              .trackDurationWith(_.toMetricsSeconds)
        case r: IssueCredentialRecord =>
          ZIO.logWarning(s"Invalid candidate record received for processing: $r") *> ZIO.unit
      }
    } yield ()

    exchange
  }

}
