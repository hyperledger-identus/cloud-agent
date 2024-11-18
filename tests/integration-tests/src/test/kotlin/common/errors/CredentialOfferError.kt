package common.errors

import org.hyperledger.identus.client.models.Connection
import org.hyperledger.identus.client.models.CreateIssueCredentialRecordRequest
import java.util.UUID

enum class CredentialOfferError {
    UNKNOWN_CONNECTION_ID {
        override fun updateCredentialWithError(credentialOffer: CreateIssueCredentialRecordRequest): CreateIssueCredentialRecordRequest {
            return CreateIssueCredentialRecordRequest(
                schemaId = credentialOffer.schemaId,
                claims = credentialOffer.claims,
                issuingDID = credentialOffer.issuingDID,
                issuingKid = credentialOffer.issuingKid,
                connectionId = UUID.randomUUID(),
                validityPeriod = credentialOffer.validityPeriod,
                credentialFormat = credentialOffer.credentialFormat,
                automaticIssuance = credentialOffer.automaticIssuance,
            )
        }
    }, ;

    abstract fun updateCredentialWithError(credentialOffer: CreateIssueCredentialRecordRequest): CreateIssueCredentialRecordRequest
}
