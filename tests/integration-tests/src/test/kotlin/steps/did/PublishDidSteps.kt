package steps.did

import abilities.ListenToEvents
import common.TestConstants
import interactions.Get
import interactions.Post
import io.cucumber.java.en.*
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import io.iohk.atala.automation.utils.Wait
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.client.models.*
import kotlin.time.Duration.Companion.seconds

class PublishDidSteps {
    @Given("{actor} creates unpublished DID")
    fun createsUnpublishedDid(actor: Actor) {
        val createDidRequest = CreateManagedDidRequest(
            CreateManagedDidRequestDocumentTemplate(
                publicKeys = listOf(
                    ManagedDIDKeyTemplate("auth-1", Purpose.AUTHENTICATION, Curve.SECP256K1),
                    ManagedDIDKeyTemplate("auth-2", Purpose.AUTHENTICATION, Curve.ED25519),
                    ManagedDIDKeyTemplate("assertion-1", Purpose.ASSERTION_METHOD, Curve.SECP256K1),
                    ManagedDIDKeyTemplate("comm-1", Purpose.KEY_AGREEMENT, Curve.X25519),
                ),
                services = listOf(
                    Service("https://foo.bar.com", listOf("LinkedDomains"), Json("https://foo.bar.com/")),
                    Service("https://update.com", listOf("LinkedDomains"), Json("https://update.com/")),
                    Service("https://remove.com", listOf("LinkedDomains"), Json("https://remove.com/")),
                ),
            ),
        )
        actor.attemptsTo(
            Post.to("/did-registrar/dids")
                .with {
                    it.body(createDidRequest)
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
        )

        val managedDid = SerenityRest.lastResponse().get<ManagedDID>()

        actor.attemptsTo(
            Ensure.that(managedDid.longFormDid!!).isNotEmpty(),
            Get.resource("/did-registrar/dids/${managedDid.longFormDid}"),
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )

        val did = SerenityRest.lastResponse().get<ManagedDID>()

        actor.remember("longFormDid", managedDid.longFormDid)
        actor.remember("shortFormDid", did.did)
        actor.forget<String>("hasPublishedDid")
    }

    @Given("{actor} has a published DID")
    fun agentHasAPublishedDID(agent: Actor) {
        if (agent.recallAll().containsKey("hasPublishedDid")) {
            return
        }
        if (!agent.recallAll().containsKey("shortFormDid") &&
            !agent.recallAll().containsKey("longFormDid")
        ) {
            createsUnpublishedDid(agent)
        }
        hePublishesDidToLedger(agent)
    }

    @Given("{actor} has an unpublished DID")
    fun agentHasAnUnpublishedDID(agent: Actor) {
        if (agent.recallAll().containsKey("shortFormDid") ||
            agent.recallAll().containsKey("longFormDid")
        ) {
            // is not published
            if (!agent.recallAll().containsKey("hasPublishedDid")) {
                return
            }
        }
        createsUnpublishedDid(agent)
    }

    @When("{actor} publishes DID to ledger")
    fun hePublishesDidToLedger(actor: Actor) {
        actor.attemptsTo(
            Post.to("/did-registrar/dids/${actor.recall<String>("shortFormDid")}/publications"),
        )
        val didOperationResponse = SerenityRest.lastResponse().get<DIDOperationResponse>()

        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(HttpStatus.SC_ACCEPTED),
            Ensure.that(didOperationResponse.scheduledOperation.didRef).isNotEmpty(),
            Ensure.that(didOperationResponse.scheduledOperation.id).isNotEmpty(),
        )

        Wait.until(
            timeout = 30.seconds,
            errorMessage = "ERROR: DID was not published to ledger!",
        ) {
            val didEvent = ListenToEvents.with(actor).didEvents.lastOrNull {
                it.data.did == actor.recall<String>("shortFormDid")
            }
            didEvent != null && didEvent.data.status == "PUBLISHED"
        }
        actor.attemptsTo(
            Get.resource("/dids/${actor.recall<String>("shortFormDid")}"),
        )

        val didDocument = SerenityRest.lastResponse().get<DIDResolutionResult>().didDocument!!

        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
            Ensure.that(didDocument.id).isEqualTo(actor.recall("shortFormDid")),
        )

        actor.remember("hasPublishedDid", true)
    }

    @Then("{actor} resolves DID document corresponds to W3C standard")
    fun heSeesDidDocumentCorrespondsToW3cStandard(actor: Actor) {
        val didResolutionResult = SerenityRest.lastResponse().get<DIDResolutionResult>()
        val didDocument = didResolutionResult.didDocument!!
        val shortFormDid = actor.recall<String>("shortFormDid")
        actor.attemptsTo(
            Ensure.that(didDocument.id).isEqualTo(shortFormDid),
            Ensure.that(didDocument.authentication!![0])
                .isEqualTo("$shortFormDid#${TestConstants.PRISM_DID_AUTH_KEY.id}"),
            Ensure.that(didDocument.verificationMethod!![0].controller).isEqualTo(shortFormDid),
            Ensure.that(didResolutionResult.didDocumentMetadata.deactivated!!).isFalse(),
        )
    }
}
