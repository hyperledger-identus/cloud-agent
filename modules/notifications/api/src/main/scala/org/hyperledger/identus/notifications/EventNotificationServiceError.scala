package org.hyperledger.identus.notifications

sealed trait EventNotificationServiceError

object EventNotificationServiceError {
  case class EventSendingFailed(msg: String) extends EventNotificationServiceError
}
