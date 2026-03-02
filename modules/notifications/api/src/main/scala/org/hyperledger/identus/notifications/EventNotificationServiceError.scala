package org.hyperledger.identus.notifications

import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait EventNotificationServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "EventNotificationServiceError"
}

object EventNotificationServiceError {
  case class EventSendingFailed(msg: String)
      extends EventNotificationServiceError(StatusCode.InternalServerError, s"Event sending failed: $msg")
}
