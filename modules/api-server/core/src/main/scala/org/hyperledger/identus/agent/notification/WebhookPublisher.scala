package org.hyperledger.identus.agent.notification

import org.hyperledger.identus.agent.notification.JsonEventEncoders.*
import org.hyperledger.identus.agent.notification.WebhookPublisherError.UnexpectedError
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.model.ManagedDIDDetail
import org.hyperledger.identus.agent.walletapi.service.WalletManagementService
import org.hyperledger.identus.connect.core.model.ConnectionRecord
import org.hyperledger.identus.event.notification.{
  Event,
  EventConsumer,
  EventNotificationConfig,
  EventNotificationService
}
import org.hyperledger.identus.pollux.core.model.{IssueCredentialRecord, PresentationRecord}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.http.*
import zio.json.*

class WebhookPublisher(
    appConfig: AppConfig,
    notificationService: EventNotificationService,
    walletService: WalletManagementService,
    client: Client
) {

  private val config = appConfig.agent.webhookPublisher

  private val baseHeaders = Headers(Header.ContentType(MediaType.application.json))

  private val globalWebhookBaseHeaders = config.apiKey
    .map(key => Headers(Header.Authorization.Bearer(key)))
    .getOrElse(Headers.empty)

  private val parallelism = config.parallelism.getOrElse(1).max(1).min(10)

  val run: ZIO[Client, WebhookPublisherError, Unit] = {
    for {
      connectConsumer <- notificationService
        .consumer[ConnectionRecord]("Connect")
        .mapError(e => UnexpectedError(e.toString))
      issueConsumer <- notificationService
        .consumer[IssueCredentialRecord]("Issue")
        .mapError(e => UnexpectedError(e.toString))
      presentationConsumer <- notificationService
        .consumer[PresentationRecord]("Presentation")
        .mapError(e => UnexpectedError(e.toString))
      didStateConsumer <- notificationService
        .consumer[ManagedDIDDetail]("DIDDetail")
        .mapError(e => UnexpectedError(e.toString))
      _ <- pollAndNotify(connectConsumer).forever.debug.forkDaemon
      _ <- pollAndNotify(issueConsumer).forever.debug.forkDaemon
      _ <- pollAndNotify(presentationConsumer).forever.debug.forkDaemon
      _ <- pollAndNotify(didStateConsumer).forever.debug.forkDaemon
    } yield ()
  }

  private def pollAndNotify[A](consumer: EventConsumer[A])(implicit encoder: JsonEncoder[A]) = {
    for {
      _ <- ZIO.logDebug(s"Polling $parallelism event(s)")
      events <- consumer.poll(parallelism).mapError(e => UnexpectedError(e.toString))
      _ <- ZIO.logDebug(s"Got ${events.size} event(s)")
      webhookConfig <- ZIO
        .foreach(events.map(_.walletId).toSet.toList) { walletId =>
          walletService.listWalletNotifications
            .map(walletId -> _)
            .provide(ZLayer.succeed(WalletAccessContext(walletId)))
        }
        .map(_.toMap)
      notifyTasks = events.flatMap { e =>
        val webhooks = webhookConfig.getOrElse(e.walletId, Nil)
        generateNotifyWebhookTasks(e, webhooks)
          .map(
            _.retry(Schedule.spaced(5.second) && Schedule.recurs(2))
              .catchAll(e => ZIO.logError(s"Webhook permanently failing, with last error being: ${e.msg}"))
          )
      }
      _ <- ZIO.collectAllParDiscard(notifyTasks).withParallelism(parallelism)
    } yield ()
  }

  private def generateNotifyWebhookTasks[A](
      event: Event[A],
      webhooks: Seq[EventNotificationConfig]
  )(implicit encoder: JsonEncoder[A]): Seq[ZIO[Client, UnexpectedError, Unit]] = {
    val globalWebhookTarget = config.url.map(_ -> globalWebhookBaseHeaders).toSeq
    val walletWebhookTargets = webhooks
      .map(i => i.url -> i.customHeaders)
      .map { case (url, headers) =>
        url -> headers.foldLeft(Headers.empty) { case (acc, (k, v)) => acc.addHeader(Header.Custom(k, v)) }
      }
    (walletWebhookTargets ++ globalWebhookTarget)
      .map { case (url, headers) => notifyWebhook(event, url.toString, headers) }
  }

  private def notifyWebhook[A](event: Event[A], url: String, headers: Headers)(implicit
      encoder: JsonEncoder[A]
  ): ZIO[Client, UnexpectedError, Unit] = {
    val result = for {
      _ <- ZIO.logDebug(s"Sending event: $event to HTTP webhook URL: $url.")
      url <- ZIO.fromEither(URL.decode(url)).orDie
      response <- Client
        .streaming(
          Request(
            url = url,
            method = Method.POST,
            headers = baseHeaders ++ headers,
            body = Body.fromString(event.toJson)
          )
        )
        .timeoutFail(new RuntimeException("Client request timed out"))(5.seconds)
        .mapError(t => UnexpectedError(s"Webhook request error: $t"))
      resp <-
        if response.status.isSuccess then ZIO.unit
        else {
          ZIO.fail(
            UnexpectedError(
              s"Failed - Unsuccessful webhook response: [status: ${response.status}]" // TODO Restore error message in this unexpected error reporting
            )
          )
        }
    } yield resp
    result.provide(ZLayer.succeed(client) ++ Scope.default)
  }
}

object WebhookPublisher {
  val layer: URLayer[AppConfig & EventNotificationService & WalletManagementService & Client, WebhookPublisher] =
    ZLayer.fromFunction(WebhookPublisher(_, _, _, _))
}
