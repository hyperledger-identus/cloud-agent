package org.hyperledger.identus.event.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.event.notification.EventNotificationConfig
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.*

import java.time.Instant
import java.util.UUID
import scala.language.implicitConversions

final case class WebhookNotification(
    @description(WebhookNotification.annotations.id.description)
    @encodedExample(WebhookNotification.annotations.id.example)
    id: UUID,
    @description(WebhookNotification.annotations.url.description)
    @encodedExample(WebhookNotification.annotations.url.example)
    url: String,
    customHeaders: Map[String, String],
    @description(WebhookNotification.annotations.createdAt.description)
    @encodedExample(WebhookNotification.annotations.createdAt.example)
    createdAt: Instant
)

object WebhookNotification {
  given encoder: JsonEncoder[WebhookNotification] = DeriveJsonEncoder.gen[WebhookNotification]
  given decoder: JsonDecoder[WebhookNotification] = DeriveJsonDecoder.gen[WebhookNotification]
  given schema: Schema[WebhookNotification] = Schema.derived

  object annotations {
    object id
        extends Annotation[UUID](
          description = "ID of webhook notification resource",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )

    object url
        extends Annotation[String](
          description = "A URL of webhook for event notification",
          example = "http://example.com"
        )

    object createdAt
        extends Annotation[Instant](
          description = "A time which the webhook notification resource was created.",
          example = Instant.EPOCH
        )
  }

  given Conversion[EventNotificationConfig, WebhookNotification] = notification =>
    WebhookNotification(
      id = notification.id,
      url = notification.url.toString(),
      customHeaders = notification.customHeaders,
      createdAt = notification.createdAt
    )

}

final case class WebhookNotificationPage(
    self: String,
    kind: String = "WebhookNotificationPage",
    pageOf: String,
    next: Option[String] = None,
    previous: Option[String] = None,
    contents: Seq[WebhookNotification]
)

object WebhookNotificationPage {
  given encoder: JsonEncoder[WebhookNotificationPage] = DeriveJsonEncoder.gen[WebhookNotificationPage]
  given decoder: JsonDecoder[WebhookNotificationPage] = DeriveJsonDecoder.gen[WebhookNotificationPage]
  given schema: Schema[WebhookNotificationPage] = Schema.derived
}
