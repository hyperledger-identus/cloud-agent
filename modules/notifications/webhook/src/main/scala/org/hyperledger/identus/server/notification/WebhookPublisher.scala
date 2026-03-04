package org.hyperledger.identus.server.notification

import org.hyperledger.identus.notifications.{Event, JsonEventConsumer}
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.server.notification.WebhookPublisherError.UnexpectedError
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.notifications.EventNotificationConfig
import org.hyperledger.identus.wallet.service.WalletManagementService
import zio.*
import zio.http.*
import zio.json.*
import zio.json.ast.Json

import java.util.UUID

class WebhookPublisher(
    appConfig: AppConfig,
    consumers: Seq[JsonEventConsumer],
    walletService: WalletManagementService,
    client: Client
) {

  private val config = appConfig.agent.webhookPublisher

  private val baseHeaders = Headers(Header.ContentType(MediaType.application.json))

  private val globalWebhookBaseHeaders = config.apiKey
    .map(key => Headers(Header.Authorization.Bearer(key)))
    .getOrElse(Headers.empty)

  private val parallelism = config.parallelism.getOrElse(1).max(1).min(10)

  private given JsonEncoder[WalletId] = summon[JsonEncoder[UUID]].contramap(_.toUUID)
  private given JsonEncoder[Event[Json]] = DeriveJsonEncoder.gen[Event[Json]]

  val run: ZIO[Client, WebhookPublisherError, Unit] = {
    for {
      _ <- ZIO.foreach(consumers)(c => pollAndNotify(c).forever.debug.forkDaemon)
    } yield ()
  }

  private def pollAndNotify(consumer: JsonEventConsumer) = {
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

  private def generateNotifyWebhookTasks(
      event: Event[Json],
      webhooks: Seq[EventNotificationConfig]
  ): Seq[ZIO[Client, UnexpectedError, Unit]] = {
    val globalWebhookTarget = config.url.map(_ -> globalWebhookBaseHeaders).toSeq
    val walletWebhookTargets = webhooks
      .map(i => i.url -> i.customHeaders)
      .map { case (url, headers) =>
        url -> headers.foldLeft(Headers.empty) { case (acc, (k, v)) => acc.addHeader(Header.Custom(k, v)) }
      }
    (walletWebhookTargets ++ globalWebhookTarget)
      .map { case (url, headers) => notifyWebhook(event, url.toString, headers) }
  }

  private def notifyWebhook(event: Event[Json], url: String, headers: Headers): ZIO[Client, UnexpectedError, Unit] = {
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
              s"Failed - Unsuccessful webhook response: [status: ${response.status}]"
            )
          )
        }
    } yield resp
    result.provide(ZLayer.succeed(client) ++ Scope.default)
  }
}
