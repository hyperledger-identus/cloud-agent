package org.hyperledger.identus.didcomm.controller

import org.hyperledger.identus.mercury.model.{DidId, PIURI}
import org.hyperledger.identus.shared.models.{Failure, KeyId, StatusCode}

sealed trait DIDCommControllerError extends Failure {
  override def namespace = "DIDCommControllerError"
}

object DIDCommControllerError {
  final case class InvalidContentType(maybeContentType: Option[String]) extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = maybeContentType match
      case Some(value) => s"The 'content-type' request header value is invalid: $value"
      case None        => s"The 'content-type' request header is undefined"
  }

  object RecipientNotFoundError extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.UnprocessableContent
    override def userFacingMessage: String = "Recipient not found in the DIDComm Message"
  }

  final case class UnexpectedError(statusCode: StatusCode) extends DIDCommControllerError {
    override def userFacingMessage: String = "An unexpected error occurred while processing your request"
  }

  final case class RequestBodyParsingError(cause: String) extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"Unable to parse the request body as a valid DIDComm message: $cause"
  }

  final case class PeerDIDNotFoundError(did: DidId) extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.UnprocessableContent
    override def userFacingMessage: String = s"The Peer DID was not found in this agent: ${did.value}"
  }

  final case class PeerDIDKeyNotFoundError(did: DidId, keyId: KeyId) extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.UnprocessableContent
    override def userFacingMessage: String =
      s"The Peer DID does not contain the required key: DID=${did.value}, keyId=${keyId.value}"
  }

  final case class UnsupportedPIURI(piuri: PIURI) extends DIDCommControllerError {
    override def statusCode: StatusCode = StatusCode.UnprocessableContent
    override def userFacingMessage: String =
      s"The Protocol Identifier URI (URI) found in the DIDComm message is not supported: PIURI=$piuri"
  }
}
