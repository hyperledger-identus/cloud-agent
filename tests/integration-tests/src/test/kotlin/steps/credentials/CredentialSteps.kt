package steps.credentials

import abilities.ListenToEvents
import common.*
import interactions.Post
import interactions.body
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import io.iohk.atala.automation.serenity.interactions.PollingWait
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.*
import org.hamcrest.CoreMatchers.equalTo
import org.hyperledger.identus.client.models.*
import org.hyperledger.identus.client.models.IssueCredentialRecord.ProtocolState.*

class CredentialSteps {

    @When("{actor} receives the credential offer")
    fun holderReceivesCredentialOffer(holder: Actor) {
        holder.attemptsTo(
            PollingWait.until(
                ListenToEvents.credentialState(holder),
                equalTo(OFFER_RECEIVED),
            ),
        )
        val recordId = ListenToEvents.with(holder).credentialEvents.last().data.recordId
        holder.remember("recordId", recordId)
    }

    @When("{actor} tries to issue the credential")
    fun issuerTriesToIssueTheCredential(issuer: Actor) {
        issuer.attemptsTo(
            PollingWait.until(
                ListenToEvents.credentialState(issuer),
                equalTo(REQUEST_RECEIVED),
            ),
        )

        val recordId = ListenToEvents.with(issuer).credentialEvents.last().data.recordId

        issuer.attemptsTo(
            Post.to("/issue-credentials/records/$recordId/issue-credential"),
        )
    }

    @When("{actor} prepares the credential in '{}' format using the '{}' API")
    fun issuerPreparesTheCredentialInFormat(issuer: Actor, credentialType: CredentialType, apiVersion: CreateCredentialOfferAPIVersion) {
        issuer.remember("currentCredentialType", credentialType)
        issuer.remember("currentAPI", apiVersion)
    }

    @When("{actor} prepares the '{}' to issue the credential")
    fun issuerPreparesTheSchemaToIssueTheCredential(issuer: Actor, schema: CredentialSchema) {
        issuer.remember("currentSchema", schema)
    }

    @When("{actor} prepares to use a '{}' form of DID with key id '{}'")
    fun issuerPreparesTheDIDWithTheKeyId(issuer: Actor, didForm: String, assertionKey: String) {
        val did: String = if (didForm == "short") {
            issuer.recall("shortFormDid")
        } else {
            issuer.recall("longFormDid")
        }
        issuer.remember("currentDID", did)
        issuer.remember("currentAssertionKey", assertionKey)
    }

    @When("{actor} prepares the claims '{}' for the credential")
    fun issuerPreparesTheClaims(issuer: Actor, credentialClaims: CredentialClaims) {
        issuer.remember("currentClaims", credentialClaims.claims)
    }

    @When("{actor} sends the prepared credential offer to {actor}")
    fun issuerSendsTheCredentialOfferToHolder(issuer: Actor, holder: Actor) {
        val api = issuer.recall<CreateCredentialOfferAPIVersion>("currentAPI")
        val credentialType = issuer.recall<CredentialType>("currentCredentialType")
        val did = issuer.recall<String>("currentDID")

        val assertionKey = issuer.recall<String>("currentAssertionKey")
        val schema = issuer.recall<CredentialSchema>("currentSchema")
        val schemaGuid = issuer.recall<String>(schema.name)
        val claims = issuer.recall<Map<String, Any>>("currentClaims")

        val connectionId = issuer.recall<Connection>("connection-with-${holder.name}").connectionId

        val schemaUrl: String? = schemaGuid?.let {
            "${issuer.recall<String>("baseUrl")}/schema-registry/schemas/$it"
        }

        val credentialOfferRequest = when (credentialType) {
            CredentialType.JWT_VCDM_1_1 -> api.buildJWTCredentialOfferRequest(credentialType, did, assertionKey, schemaUrl!!, claims, connectionId)
            CredentialType.SD_JWT_VCDM_1_1 -> api.buildSDJWTCredentialOfferRequest(credentialType, did, assertionKey, schemaUrl!!, claims, connectionId)
            else -> throw IllegalArgumentException("Unsupported credential type: $credentialType")
        }

        issuer.attemptsTo(
            Post.to("/issue-credentials/credential-offers").body(credentialOfferRequest),
        )

        saveCredentialOffer(issuer, holder)
    }

    private fun saveCredentialOffer(issuer: Actor, holder: Actor) {
        val credentialRecord = SerenityRest.lastResponse().get<IssueCredentialRecord>()

        issuer.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
        )

        issuer.remember("thid", credentialRecord.thid)
        holder.remember("thid", credentialRecord.thid)
    }

    @When("{actor} issues the credential")
    fun issuerIssuesTheCredential(issuer: Actor) {
        issuerTriesToIssueTheCredential(issuer)

        issuer.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
            PollingWait.until(
                ListenToEvents.credentialState(issuer),
                equalTo(CREDENTIAL_SENT),
            ),
        )
        issuer.remember("issuedCredential", ListenToEvents.with(issuer).credentialEvents.last().data)
    }

    @Then("{actor} receives the issued credential")
    fun holderReceivesTheIssuedCredential(holder: Actor) {
        holder.attemptsTo(
            PollingWait.until(
                ListenToEvents.credentialState(holder),
                equalTo(CREDENTIAL_RECEIVED),
            ),
        )

        val issueCredentialRecord = ListenToEvents.with(holder).credentialEvents.last().data

        issueCredentialRecord.credential?.let {
            println("Issued Credentials:")
            println(it)
        }

        holder.remember("issuedCredential", issueCredentialRecord)
    }

    @Then("{actor} should see that credential issuance has failed")
    fun issuerShouldSeeThatCredentialIssuanceHasFailed(issuer: Actor) {
        issuer.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_UNPROCESSABLE_ENTITY),
        )
    }
}
