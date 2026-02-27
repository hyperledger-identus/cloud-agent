package org.hyperledger.identus.agent.server.jobs

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.service.{ManagedDIDService, WalletManagementService}
import org.hyperledger.identus.shared.messaging.{Message, MessagingService, Producer}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext, WalletId}
import org.hyperledger.identus.shared.utils.DurationOps.toMetricsSeconds
import zio.*
import zio.metrics.Metric

object DIDStateSyncBackgroundJobs extends BackgroundJobsHelper {

  private val TOPIC_NAME = "sync-did-state"

  val didStateSyncTrigger = {
    (for {
      config <- ZIO.service[AppConfig]
      producer <- ZIO.service[Producer[WalletId, WalletId]]
      trigger = for {
        walletManagementService <- ZIO.service[WalletManagementService]
        wallets <- walletManagementService.listWallets().map(_._1)
        _ <- ZIO.logInfo(s"Triggering DID state sync for '${wallets.size}' wallets")
        _ <- ZIO.foreach(wallets)(w => producer.produce(TOPIC_NAME, w.id, w.id))
      } yield ()
      _ <- trigger
        .catchAll(e => ZIO.logError(s"error while syncing DID publication state: $e"))
        .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))
        .repeat(Schedule.spaced(config.pollux.didStateSyncTriggerRecurrenceDelay))
    } yield ()).debug.fork
  }

  val didStateSyncHandler = for {
    appConfig <- ZIO.service[AppConfig]
    _ <- MessagingService.consumeWithRetryStrategy(
      "identus-cloud-agent",
      DIDStateSyncBackgroundJobs.handleMessage,
      retryStepsFromConfig(TOPIC_NAME, appConfig.agent.messagingService.didStateSync)
    )
  } yield ()

  private def handleMessage(message: Message[WalletId, WalletId]): RIO[ManagedDIDService, Unit] = {
    val effect = for {
      managedDidService <- ZIO.service[ManagedDIDService]
      _ <- managedDidService.syncManagedDIDState
      _ <- managedDidService.syncUnconfirmedUpdateOperations
    } yield ()
    effect
      .provideSomeLayer(ZLayer.succeed(WalletAccessContext(message.value)))
      .catchAll(t => ZIO.logErrorCause("Unable to syncing DID publication state", Cause.fail(t)))
      @@ Metric
        .gauge("did_publication_state_sync_job_ms_gauge")
        .trackDurationWith(_.toMetricsSeconds)
  }
}
