package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.shared.http.GenericUriResolverError
import org.hyperledger.identus.shared.json.JsonSchemaError
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait PresentationError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace = "Presentation"
}

object PresentationError {

  final case class RecordIdNotFound(recordId: DidCommID)
      extends PresentationError(
        StatusCode.NotFound,
        s"Record Id not found: $recordId"
      )

  final case class ThreadIdNotFound(thid: DidCommID)
      extends PresentationError(
        StatusCode.NotFound,
        s"Thread Id not found: $thid"
      )

  final case class NoThreadIdFoundInRecord(presentationId: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        s"Presentation record has missing ThreadId for record: $presentationId"
      )

  final case class InvalidFlowStateError(msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        msg
      )

  final case class RequestPresentationHasMultipleAttachment(presentationId: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"Request Presentation with multi attachments: $presentationId"
      )

  final case class RequestPresentationMissingField(presentationId: String, field: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"Request Presentation missing $field field: $presentationId"
      )

  final case class IssuedCredentialNotFoundError(cause: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        "Issued credential not found"
      )

  final case class SchemaURIDereferencingError(error: GenericUriResolverError)
      extends PresentationError(
        error.statusCode,
        error.userFacingMessage
      )

  final case class CredentialDefinitionURIDereferencingError(error: GenericUriResolverError)
      extends PresentationError(
        error.statusCode,
        error.userFacingMessage
      )

  final case class PresentationDecodingError(cause: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        s"Presentation decoding error: $cause"
      )

  final case class HolderBindingError(msg: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        s"Holder binding error: $msg"
      )

  object MissingCredential
      extends PresentationError(
        StatusCode.BadRequest,
        s"The Credential is missing from attachments"
      )

  object MissingCredentialFormat
      extends PresentationError(
        StatusCode.BadRequest,
        s"The Credential format is missing from the credential in attachment"
      )

  final case class UnsupportedCredentialFormat(vcFormat: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"The Credential format '$vcFormat' is not Unsupported"
      )
  final case class UnsupportedCredentialFormatBecauseDisabled(detail: String)
      extends PresentationError(
        StatusCode.BadRequest,
        userFacingMessage = detail
      )

  final case class InvalidAnoncredPresentationRequest(error: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        error
      )

  final case class InvalidAnoncredPresentation(error: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        error
      )
  object MissingConnectionIdForPresentationRequest
      extends PresentationError(
        StatusCode.BadRequest,
        s"Presentation Request missing connectionId"
      )

  final case class MissingAnoncredPresentationRequest(error: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        error
      )
  final case class MissingSDJWTPresentationRequest(error: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        error
      )

  final case class NotMatchingPresentationCredentialFormat(cause: Throwable)
      extends PresentationError(
        StatusCode.BadRequest,
        s"Presentation and Credential Format Not Matching: ${cause.toString}"
      )

  final case class AnoncredPresentationCreationError(cause: Throwable)
      extends PresentationError(
        StatusCode.InternalServerError,
        cause.toString
      )

  final case class AnoncredCredentialProofParsingError(cause: String)
      extends PresentationError(
        StatusCode.BadRequest,
        cause
      )

  final case class AnoncredPresentationParsingError(cause: JsonSchemaError)
      extends PresentationError(
        StatusCode.BadRequest,
        cause.error
      )

  final case class AnoncredPresentationVerificationError(cause: Throwable)
      extends PresentationError(
        StatusCode.BadRequest,
        cause.toString
      )

  final case class NoCredentialFoundInRecord(presentationId: DidCommID)
      extends PresentationError(
        StatusCode.InternalServerError,
        s"Presentation record has missing ThreadId for record: $presentationId"
      )

  final case class NotValidDidCommID(id: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"$id is not a valid DidCommID"
      )

  final case class PresentationNotFound(error: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"Error occurred while getting Presentation records: $error"
      )

  final case class DIDResolutionFailed(did: String, msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        s"DIDResolutionFailed for $did: $msg"
      )

  final case class DIDDocumentMissing(did: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        s"Did Document is missing the required publicKey: $did"
      )

  final case class PublicKeyDecodingError(msg: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        msg
      )

  final case class PresentationVerificationError(msg: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        msg
      )

  final case class PresentationValidationError(msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        msg
      )

  final case class PresentationReceivedError(msg: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        msg
      )

  final case class RequestPresentationDecodingError(msg: String)
      extends PresentationError(
        StatusCode.InternalServerError,
        msg
      )

  final case class InvitationParsingError(cause: String)
      extends PresentationError(
        StatusCode.BadRequest,
        cause
      )

  final case class InvitationExpired(msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        msg
      )

  final case class InvitationAlreadyReceived(msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        msg
      )

  final case class MissingInvitationAttachment(msg: String)
      extends PresentationError(
        StatusCode.BadRequest,
        msg
      )

}
