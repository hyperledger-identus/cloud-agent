package common

import org.hyperledger.identus.client.models.*

object TestConstants {
    val TESTS_CONFIG =
        System.getProperty("TESTS_CONFIG")
            ?: System.getenv("TESTS_CONFIG")
            ?: "/configs/basic_neoprism.conf"
    val TEST_VERIFICATION_POLICY = VerificationPolicyInput(
        name = "Trusted Issuer and SchemaID",
        description = "Verification Policy with trusted issuer and schemaId",
        constraints = listOf(
            VerificationPolicyConstraint(
                schemaId = "http://atalaprism.io/schemas/1.0/StudentCredential",
                trustedIssuers = listOf(
                    "did:example:123456789abcdefghi",
                    "did:example:123456789abcdefghj",
                ),
            ),
        ),
    )

    val EVENT_TYPE_CONNECTION_UPDATED = "ConnectionUpdated"
    val EVENT_TYPE_ISSUE_CREDENTIAL_RECORD_UPDATED = "IssueCredentialRecordUpdated"
    val EVENT_TYPE_PRESENTATION_UPDATED = "PresentationUpdated"
    val EVENT_TYPE_DID_STATUS_UPDATED = "DIDStatusUpdated"
    val WRONG_SEED = "wrong seed"
}
