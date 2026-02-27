package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.agent.walletapi.model.PublicationState
import org.hyperledger.identus.castor.core.model.did.{PrismDID, VerificationRelationship}
import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.ProtocolState
import org.hyperledger.identus.shared.models.{Failure, KeyId, StatusCode}

import java.util.UUID

sealed trait CredentialServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "CredentialServiceError"
}

object CredentialServiceError {
  final case class InvitationExpired(expiryTime: Long)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"The invitation has expired: expiryTime=$expiryTime"
      )

  final case class InvitationAlreadyReceived(invitationId: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"The invitation has already been received: invitationId=$invitationId"
      )

  final case class InvitationParsingError(cause: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        cause
      )
  final case class MissingInvitationAttachment(cause: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        cause
      )
  final case class CredentialOfferDecodingError(cause: String)
      extends CredentialServiceError(
        StatusCode.InternalServerError,
        s"Credential Offer decoding error: $cause"
      )
  final case class CredentialOfferMissingField(recordId: String, missingField: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"Credential Offer missing $missingField field: $recordId"
      )

  final case class InvalidCredentialOffer(cause: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"The credential offer message is invalid: cause[$cause]"
      )

  final case class UnsupportedDidFormat(did: String)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The DID format is not supported: did=$did"
      )

  final case class CredentialDefinitionServiceError(cause: String)
      extends CredentialServiceError(
        StatusCode.InternalServerError,
        s"An error occurred related the credential definition: cause[$cause]"
      )

  final case class CredentialDefinitionPrivatePartNotFound(guid: UUID)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"There is no private part matching the credential definition: guid=$guid"
      )

  final case class RecordNotFound(recordId: DidCommID, state: Option[ProtocolState] = None)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"The requested record was not found: recordId=$recordId, state=$state"
      )

  final case class NoSubjectFoundInRecord(recordId: DidCommID)
      extends CredentialServiceError(
        StatusCode.InternalServerError,
        s"VC SubjectId not found in credential record: $recordId"
      )

  final case class RecordNotFoundForThreadIdAndStates(thid: DidCommID, states: ProtocolState*)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"The requested record was not found: thid=${thid.value}, states:${states.mkString(",")}"
      )

  final case class DIDNotFoundInWallet(did: PrismDID)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"The requested DID does not exist in the wallet: did=${did.toString}"
      )

  final case class KeyPairNotFoundInWallet(did: PrismDID, keyId: KeyId, algo: String)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"The requested key pair does not exist in the wallet: did=${did.toString}, keyId=$keyId, algo=$algo"
      )

  final case class DIDNotPublished(did: PrismDID, state: PublicationState)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The DID must be published for this operation: did=${did.toString}, publicationState=$state"
      )

  final case class DIDNotResolved(did: PrismDID)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"The requested DID cannot be resolved: did=${did.toString}"
      )

  final case class KeyNotFoundInDID(did: PrismDID, verificationRelationship: VerificationRelationship)
      extends CredentialServiceError(
        StatusCode.NotFound,
        s"A key with the given purpose was not found in the DID: did=${did.toString}, purpose=${verificationRelationship.name}"
      )
  final case class MultipleKeysWithSamePurposeFoundInDID(
      did: PrismDID,
      verificationRelationship: VerificationRelationship
  ) extends CredentialServiceError(
        StatusCode.BadRequest,
        s"A key with the given purpose was found multiple times in the DID: did=${did.toString}, purpose=${verificationRelationship.name}"
      )
  final case class InvalidCredentialRequest(cause: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"The credential request message is invalid: cause[$cause]"
      )

  final case class InvalidCredentialIssue(cause: String)
      extends CredentialServiceError(
        StatusCode.BadRequest,
        s"The credential issue message is invalid: cause[$cause]"
      )

  final case class InvalidStateForOperation(state: ProtocolState)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The current record state not valid for the requested operation: cause[$state]"
      )

  final case class VCClaimsValueParsingError(cause: String)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The VC claims value could not be parsed: cause[$cause]"
      )

  final case class UnsupportedVCClaimsMediaType(mediaType: String)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The VC claims media type is not supported: mediaType=$mediaType"
      )

  final case class ExpirationDateHasPassed(expirationDate: Long)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The expiration date has passed: expirationDate=$expirationDate"
      )

  final case class CredentialRequestValidationFailed(errors: String*)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The credential request validation failed: errors=${errors.mkString("[", "], [", "]")}"
      )

  final case class VCJwtHeaderParsingError(cause: String)
      extends CredentialServiceError(
        StatusCode.UnprocessableContent,
        s"The VC Jwt Header value could not be parsed: cause[$cause]"
      )

}
