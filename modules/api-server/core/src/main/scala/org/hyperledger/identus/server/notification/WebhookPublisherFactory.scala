package org.hyperledger.identus.server.notification

import org.hyperledger.identus.connections.core.model.ConnectionRecord
import org.hyperledger.identus.credentials.core.model.{IssueCredentialRecord, PresentationRecord}
import org.hyperledger.identus.notifications.{EventNotificationService, JsonEventConsumer}
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.wallet.model.ManagedDIDDetail
import org.hyperledger.identus.wallet.service.WalletManagementService
import zio.*
import zio.http.Client

object WebhookPublisherFactory {
  val run: ZIO[AppConfig & EventNotificationService & WalletManagementService & Client, Nothing, Unit] =
    (for {
      appConfig <- ZIO.service[AppConfig]
      notificationService <- ZIO.service[EventNotificationService]
      walletService <- ZIO.service[WalletManagementService]
      client <- ZIO.service[Client]
      connectConsumer <- notificationService.consumer[ConnectionRecord]("Connect")
      issueConsumer <- notificationService.consumer[IssueCredentialRecord]("Issue")
      presentConsumer <- notificationService.consumer[PresentationRecord]("Presentation")
      didConsumer <- notificationService.consumer[ManagedDIDDetail]("DIDDetail")
      jsonConsumers = Seq(
        JsonEventConsumer.fromTyped(connectConsumer, JsonEventEncoders.encodeConnectionRecord),
        JsonEventConsumer.fromTyped(issueConsumer, JsonEventEncoders.encodeIssueCredentialRecord),
        JsonEventConsumer.fromTyped(presentConsumer, JsonEventEncoders.encodePresentationRecord),
        JsonEventConsumer.fromTyped(didConsumer, JsonEventEncoders.encodeManagedDIDDetail),
      )
      publisher = WebhookPublisher(appConfig, jsonConsumers, walletService, client)
      _ <- publisher.run
    } yield ()).catchAll(e => ZIO.logError(s"WebhookPublisher error: $e")).unit
}
