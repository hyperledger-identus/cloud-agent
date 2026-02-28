package org.hyperledger.identus.server.notification

sealed trait WebhookPublisherError

object WebhookPublisherError {
  case class InvalidWebhookURL(msg: String) extends WebhookPublisherError
  case class UnexpectedError(msg: String) extends WebhookPublisherError
}
