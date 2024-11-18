package steps.credentials

import common.CredentialSchema
import common.errors.CredentialOfferError
import interactions.Post
import interactions.body
import io.cucumber.java.en.When
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.client.models.AcceptCredentialOfferRequest
import org.hyperledger.identus.client.models.Connection
import org.hyperledger.identus.client.models.CreateIssueCredentialRecordRequest
import org.hyperledger.identus.client.models.IssueCredentialRecord

class JwtCredentialSteps {

    private fun sendCredentialOffer(
        issuer: Actor,
        holder: Actor,
        didForm: String,
        schemaGuid: String?,
        claims: Map<String, Any>,
        issuingKid: String?,
    ) {
        val did: String = if (didForm == "short") {
            issuer.recall("shortFormDid")
        } else {
            issuer.recall("longFormDid")
        }

        val schemaId: String? = if (schemaGuid != null) {
            val baseUrl = issuer.recall<String>("baseUrl")
            "$baseUrl/schema-registry/schemas/$schemaGuid"
        } else {
            null
        }

        val credentialOfferRequest = CreateIssueCredentialRecordRequest(
            schemaId = schemaId,
            claims = claims,
            issuingDID = did,
            issuingKid = issuingKid,
            connectionId = issuer.recall<Connection>("connection-with-${holder.name}").connectionId,
            validityPeriod = 3600.0,
            credentialFormat = "JWT",
            automaticIssuance = false,
        )

        issuer.attemptsTo(
            Post.to("/issue-credentials/credential-offers").body(credentialOfferRequest),
        )
    }

    private fun saveCredentialOffer(issuer: Actor, holder: Actor) {
        val credentialRecord = SerenityRest.lastResponse().get<IssueCredentialRecord>()

        issuer.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
        )

        issuer.remember("thid", credentialRecord.thid)
        holder.remember("thid", credentialRecord.thid)
    }

    @When("{actor} offers a jwt credential to {actor} with '{}' form DID")
    fun issuerOffersAJwtCredential(issuer: Actor, holder: Actor, format: String) {
        val claims = CredentialSchema.STUDENT_SCHEMA.claims

        val schemaGuid = issuer.recall<String>(CredentialSchema.STUDENT_SCHEMA.name)

        sendCredentialOffer(issuer, holder, format, schemaGuid, claims, "assertion-1")
        saveCredentialOffer(issuer, holder)
    }

    @When("{actor} offers a jwt credential to {actor} with {string} form DID using issuingKid {string} and {} schema")
    fun issuerOffersAJwtCredentialWithIssuingKeyId(
        issuer: Actor,
        holder: Actor,
        format: String,
        issuingKid: String?,
        schema: CredentialSchema,
    ) {
        val claims = schema.claims
        val schemaGuid = issuer.recall<String>(schema.name)

        sendCredentialOffer(issuer, holder, format, schemaGuid, claims, issuingKid)
        saveCredentialOffer(issuer, holder)
    }

    @When("{actor} offers a jwt credential to {actor} with '{}' form using '{}' schema")
    fun issuerOffersJwtCredentialToHolderUsingSchema(
        issuer: Actor,
        holder: Actor,
        format: String,
        schema: CredentialSchema,
    ) {
        val schemaGuid = issuer.recall<String>(schema.name)
        val claims = schema.claims
        sendCredentialOffer(issuer, holder, format, schemaGuid, claims, "assertion-1")
        saveCredentialOffer(issuer, holder)
    }

    @When("{actor} offers a jwt credential to {actor} with '{}' form DID with wrong claims structure using '{}' schema")
    fun issuerOffersJwtCredentialToHolderWithWrongClaimStructure(
        issuer: Actor,
        holder: Actor,
        format: String,
        schema: CredentialSchema,
    ) {
        val schemaGuid = issuer.recall<String>(schema.name)!!
        val claims = linkedMapOf(
            "name" to "Name",
            "surname" to "Surname",
        )
        sendCredentialOffer(issuer, holder, format, schemaGuid, claims, "assertion-1")
    }

    @When("{actor} accepts jwt credential offer using '{}' key id")
    fun holderAcceptsJwtCredentialOfferForJwt(holder: Actor, keyId: String) {
        val recordId = holder.recall<String>("recordId")
        val longFormDid = holder.recall<String>("longFormDid")
        val acceptRequest = AcceptCredentialOfferRequest(longFormDid, keyId)
        holder.attemptsTo(
            Post.to("/issue-credentials/records/$recordId/accept-offer").body(acceptRequest),
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )
    }

    @When("{actor} offers a jwt credential to {actor} with {} issue")
    fun issuerIssuesTheJwtCredentialWithIssue(
        issuer: Actor,
        holder: Actor,
        credentialOfferError: CredentialOfferError
    ) {
        val credentialOfferRequest = CreateIssueCredentialRecordRequest(
            claims = linkedMapOf(
                "name" to "Name",
                "surname" to "Surname",
            ),
            issuingDID = issuer.recall("shortFormDid"),
            issuingKid = "assertion-1",
            connectionId = issuer.recall<Connection>("connection-with-${holder.name}").connectionId,
            validityPeriod = 3600.0,
            credentialFormat = "JWT",
            automaticIssuance = false,
        )

        val credentialOfferRequestError = credentialOfferError.updateCredentialWithError(credentialOfferRequest)

        issuer.attemptsTo(
            Post.to("/issue-credentials/credential-offers").body(credentialOfferRequestError),
        )
    }
}
