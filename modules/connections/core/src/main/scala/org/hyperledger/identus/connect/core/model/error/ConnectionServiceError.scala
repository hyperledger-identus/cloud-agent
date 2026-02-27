package org.hyperledger.identus.connect.core.model.error

import org.hyperledger.identus.connect.core.model.ConnectionRecord.ProtocolState
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import zio.NonEmptyChunk

import java.util.UUID

sealed trait ConnectionServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "ConnectionServiceError"
}

object ConnectionServiceError {
  final case class UserInputValidationError(errors: NonEmptyChunk[String])
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"The provided input failed validation: errors=${errors.mkString("[", "], [", "]")}"
      )
  final case class InvitationParsingError(cause: String)
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"An error occurred while parsing the invitation content: cause=[$cause]"
      )
  final case class InvitationAlreadyReceived(invitationId: String)
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"The provided invitation has already been used: invitationId=$invitationId"
      )
  final case class RecordIdNotFound(recordId: UUID)
      extends ConnectionServiceError(
        StatusCode.NotFound,
        s"There is no connection record matching the provided identifier: recordId=$recordId"
      )
  final case class ThreadIdNotFound(thid: String)
      extends ConnectionServiceError(
        StatusCode.NotFound,
        s"There is no connection record matching the provided identifier: thid=$thid"
      )
  final case class ThreadIdMissingInReceivedMessage(msgId: String)
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"The received DIDComm message does not include a 'thid' field: messageId=$msgId"
      )
  final case class InvalidStateForOperation(state: ProtocolState)
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"The operation is not allowed for the current connection record state: state=$state"
      )
  final case class InvitationExpired(invitationId: String)
      extends ConnectionServiceError(
        StatusCode.BadRequest,
        s"The provided invitation has expired: invitationId=$invitationId"
      )
}
