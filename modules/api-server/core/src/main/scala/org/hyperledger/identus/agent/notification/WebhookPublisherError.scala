package org.hyperledger.identus.agent.notification

sealed trait WebhookPublisherError

object WebhookPublisherError {
  case class InvalidWebhookURL(msg: String) extends WebhookPublisherError
  case class UnexpectedError(msg: String) extends WebhookPublisherError
}
