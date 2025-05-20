package org.hyperledger.identus.agent.server.jobs

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.castor.core.model.did.VerificationRelationship
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.mercury.*
import org.hyperledger.identus.mercury.protocol.revocationnotificaiton.RevocationNotification
import org.hyperledger.identus.pollux.core.model.{CredInStatusList, CredentialStatusListWithCreds}
import org.hyperledger.identus.pollux.core.service.{CredentialService, CredentialStatusListService}
import org.hyperledger.identus.pollux.vc.jwt.revocation.{BitString, VCStatusList2021, VCStatusList2021Error}
import org.hyperledger.identus.resolvers.DIDResolver
import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.{Message, Producer, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.shared.utils.DurationOps.toMetricsSeconds
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json
import zio.metrics.Metric

import java.util.UUID

object StatusListJobs extends BackgroundJobsHelper {

  private val TOPIC_NAME = "sync-status-list"

  val statusListsSyncTrigger = {
    (for {
      config <- ZIO.service[AppConfig]
      producer <- ZIO.service[Producer[UUID, WalletIdAndRecordId]]
      trigger = for {
        credentialStatusListService <- ZIO.service[CredentialStatusListService]
        walletAndStatusListIds <- credentialStatusListService.getCredentialStatusListIds
        _ <- ZIO.logDebug(s"Triggering status list revocation sync for '${walletAndStatusListIds.size}' status lists")
        _ <- ZIO.foreach(walletAndStatusListIds) { (walletId, statusListId) =>
          producer.produce(TOPIC_NAME, walletId.toUUID, WalletIdAndRecordId(walletId.toUUID, statusListId))
        }
      } yield ()
      _ <- trigger.repeat(Schedule.spaced(config.pollux.statusListSyncTriggerRecurrenceDelay))
    } yield ()).debug.fork
  }

  val statusListSyncHandler = for {
    appConfig <- ZIO.service[AppConfig]
    _ <- messaging.MessagingService.consumeWithRetryStrategy(
      "identus-cloud-agent",
      StatusListJobs.handleMessage,
      retryStepsFromConfig(TOPIC_NAME, appConfig.agent.messagingService.statusListSync)
    )
  } yield ()

  private def handleMessage(message: Message[UUID, WalletIdAndRecordId]): RIO[
    DIDService & ManagedDIDService & CredentialService & DidOps & DIDResolver & HttpClient &
      CredentialStatusListService,
    Unit
  ] = {
    (for {
      _ <- ZIO.logDebug(s"Handling recordId: ${message.value} via Kafka queue")
      credentialStatusListService <- ZIO.service[CredentialStatusListService]
      walletAccessContext = WalletAccessContext(WalletId.fromUUID(message.value.walletId))
      statusListWithCreds <- credentialStatusListService
        .getCredentialStatusListWithCreds(message.value.recordId)
        .provideSome(ZLayer.succeed(walletAccessContext))
      _ <- updateStatusList(statusListWithCreds)
    } yield ()) @@ Metric
      .gauge("revocation_status_list_sync_job_ms_gauge")
      .trackDurationWith(_.toMetricsSeconds)
  }

  private def updateStatusList(statusListWithCreds: CredentialStatusListWithCreds) = {
    for {
      credentialStatusListService <- ZIO.service[CredentialStatusListService]
      vcStatusListCredString = statusListWithCreds.statusListCredential
      walletAccessContext = WalletAccessContext(statusListWithCreds.walletId)
      effect = for {
        vcStatusListCredJson <- ZIO.fromEither(vcStatusListCredString.fromJson[Json])
        issuer <- createJwtVcIssuer(statusListWithCreds.issuer, VerificationRelationship.AssertionMethod, None)
        vcStatusListCred <- VCStatusList2021
          .decodeFromJson(vcStatusListCredJson, issuer)
          .mapError(x => new Throwable(x.msg))
        bitString <- vcStatusListCred.getBitString.mapError(x => new Throwable(x.msg))
        _ <- ZIO.collectAll(
          statusListWithCreds.credentials.map(c =>
            updateBitStringForCredentialAndNotify(bitString, c, walletAccessContext)
          )
        )
        unprocessedEntityIds = statusListWithCreds.credentials.collect {
          case x if !x.isProcessed && x.isCanceled => x.id
        }
        _ <- credentialStatusListService
          .markAsProcessedMany(unprocessedEntityIds)
          @@ Metric
            .gauge("revocation_status_list_sync_mark_as_processed_many_ms_gauge")
            .trackDurationWith(_.toMetricsSeconds)

        updatedVcStatusListCred <- vcStatusListCred.updateBitString(bitString).mapError {
          case VCStatusList2021Error.EncodingError(msg: String) => new Throwable(msg)
          case VCStatusList2021Error.DecodingError(msg: String) => new Throwable(msg)
        }
        vcStatusListCredJsonString <- updatedVcStatusListCred.toJsonWithEmbeddedProof.map(_.toJson)
        _ <- credentialStatusListService.updateStatusListCredential(
          statusListWithCreds.id,
          vcStatusListCredJsonString
        )
      } yield ()
      _ <- effect
        .catchAll(e =>
          ZIO.logErrorCause(s"Error processing status list record: ${statusListWithCreds.id} ", Cause.fail(e))
        )
        .catchAllDefect(d =>
          ZIO.logErrorCause(s"Defect processing status list record: ${statusListWithCreds.id}", Cause.fail(d))
        )
        .provideSomeLayer(ZLayer.succeed(walletAccessContext))
    } yield ()
  }

  private def updateBitStringForCredentialAndNotify(
      bitString: BitString,
      credInStatusList: CredInStatusList,
      walletAccessContext: WalletAccessContext
  ) = {
    for {
      credentialService <- ZIO.service[CredentialService]
      _ <-
        if credInStatusList.isCanceled then {
          val updateBitStringEffect = bitString.setRevokedInPlace(credInStatusList.statusListIndex, true)
          val notifyEffect = sendRevocationNotificationMessage(credInStatusList)
          val updateAndNotify = for {
            updated <- updateBitStringEffect.mapError(x => new Throwable(x.message))
            _ <-
              if !credInStatusList.isProcessed then
                notifyEffect.flatMap { resp =>
                  if (resp.status >= 200 && resp.status < 300)
                    ZIO.logInfo("successfully sent revocation notification message")
                  else ZIO.logError(s"failed to send revocation notification message")
                }
              else ZIO.unit
          } yield updated
          updateAndNotify.provideSomeLayer(ZLayer.succeed(walletAccessContext)) @@ Metric
            .gauge("revocation_status_list_sync_process_single_credential_ms_gauge")
            .trackDurationWith(_.toMetricsSeconds)
        } else ZIO.unit
    } yield ()
  }

  private def sendRevocationNotificationMessage(
      credInStatusList: CredInStatusList
  ) = {
    for {
      credentialService <- ZIO.service[CredentialService]
      maybeIssueCredentialRecord <- credentialService.findById(credInStatusList.issueCredentialRecordId)
      issueCredentialRecord <- ZIO
        .fromOption(maybeIssueCredentialRecord)
        .mapError(_ =>
          new Throwable(s"Issue credential record not found by id: ${credInStatusList.issueCredentialRecordId}")
        )
      issueCredentialData <- ZIO
        .fromOption(issueCredentialRecord.issueCredentialData)
        .mapError(_ =>
          new Throwable(
            s"Issue credential data not found in issue credential record by id: ${credInStatusList.issueCredentialRecordId}"
          )
        )
      issueCredentialProtocolThreadId <- ZIO
        .fromOption(issueCredentialData.thid)
        .mapError(_ => new Throwable("thid not found in issue credential data"))
      revocationNotification = RevocationNotification.build(
        issueCredentialData.from,
        issueCredentialData.to,
        issueCredentialProtocolThreadId = issueCredentialProtocolThreadId
      )
      didCommAgent <- buildDIDCommAgent(issueCredentialData.from)
      response <- MessagingService
        .send(revocationNotification.makeMessage)
        .provideSomeLayer(didCommAgent) @@ Metric
        .gauge("revocation_status_list_sync_revocation_notification_ms_gauge")
        .trackDurationWith(_.toMetricsSeconds)
    } yield response
  }
}
