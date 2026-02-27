package org.hyperledger.identus.event.notification

sealed trait EventNotificationServiceError

object EventNotificationServiceError {
  case class EventSendingFailed(msg: String) extends EventNotificationServiceError
}
