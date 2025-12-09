package steps.did

import com.google.gson.JsonPrimitive
import interactions.Get
import interactions.Post
import interactions.body
import io.cucumber.java.en.*
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.*
import org.assertj.core.api.Assertions
import org.hyperledger.identus.client.models.*

class ManageDidSteps {

    @Given("{actor} creates {int} PRISM DIDs")
    fun createsMultipleManagedDids(actor: Actor, number: Int) {
        repeat(number) {
            createManageDidWithSecp256k1Key(actor)
        }
        actor.remember("number", number)
    }

    @When("{actor} creates PRISM DID")
    fun createManageDidWithSecp256k1Key(actor: Actor) {
        createManageDid(actor, Curve.SECP256K1, Purpose.AUTHENTICATION)
    }

    @When("{actor} creates PRISM DID with internal VDR key")
    fun createManageDidWithInternalVdrKey(actor: Actor) {
        val createDidRequest = mapOf(
            "documentTemplate" to mapOf(
                "publicKeys" to listOf(
                    mapOf(
                        "id" to "auth-1",
                        "purpose" to "authentication",
                        "curve" to "secp256k1",
                    ),
                ),
                "internalKeys" to listOf(
                    mapOf(
                        "id" to "vdr-1",
                        "purpose" to "vdr",
                    ),
                ),
                "services" to listOf(
                    mapOf(
                        "id" to "svc-1",
                        "type" to listOf("LinkedDomains"),
                        "serviceEndpoint" to "https://foo.bar.com/",
                    ),
                ),
            ),
        )

        actor.attemptsTo(
            Post.to("/did-registrar/dids").body(createDidRequest),
        )

        if (SerenityRest.lastResponse().statusCode() == SC_CREATED) {
            var createdDids = actor.recall<MutableList<String>>("createdDids")
            if (createdDids == null) {
                createdDids = mutableListOf()
            }

            val managedDid = SerenityRest.lastResponse().get<ManagedDID>()

            createdDids.add(managedDid.longFormDid!!)
            actor.remember("createdDids", createdDids)
        }
    }

    @When("{actor} creates PRISM DID with {curve} key having {purpose} purpose")
    fun createManageDid(actor: Actor, curve: Curve, purpose: Purpose) {
        val createDidRequest = createPrismDidRequest(curve, purpose)

        actor.attemptsTo(
            Post.to("/did-registrar/dids").body(createDidRequest),
        )

        if (SerenityRest.lastResponse().statusCode() == SC_CREATED) {
            var createdDids = actor.recall<MutableList<String>>("createdDids")
            if (createdDids == null) {
                createdDids = mutableListOf()
            }

            val managedDid = SerenityRest.lastResponse().get<ManagedDID>()

            createdDids.add(managedDid.longFormDid!!)
            actor.remember("createdDids", createdDids)
        }
    }

    @When("{actor} lists all PRISM DIDs")
    fun iListManagedDids(actor: Actor) {
        actor.attemptsTo(
            Get.resource("/did-registrar/dids"),
        )
    }

    @Then("{actor} sees PRISM DID was created successfully")
    fun theDidShouldBeRegisteredSuccessfully(actor: Actor) {
        val managedDid = SerenityRest.lastResponse().get<ManagedDID>()
        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
            Ensure.that(managedDid.longFormDid!!).isNotEmpty(),
        )
    }

    @Then("{actor} sees PRISM DID data was stored correctly with {curve} and {purpose}")
    fun agentSeesPrismDidWasStoredCorrectly(actor: Actor, curve: Curve, purpose: Purpose) {
        val managedDid = SerenityRest.lastResponse().get<ManagedDID>()
        Assertions.assertThat(managedDid.longFormDid).isNotNull()

        val longFormDid = managedDid.longFormDid!!
        actor.attemptsTo(
            Get("/dids/$longFormDid"),
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )

        val resolvedDid = SerenityRest.lastResponse().get<DIDResolutionResult>()
        Assertions.assertThat(resolvedDid.didDocument).isNotNull()

        val didDocument = resolvedDid.didDocument!!

        actor.attemptsTo(
            Ensure.that(didDocument.id).isEqualTo(longFormDid),
            Ensure.that(didDocument.controller!!).contains(longFormDid),
            Ensure.that(didDocument.verificationMethod!!.size).isEqualTo(1),
            Ensure.that(didDocument.verificationMethod!![0].id).contains(longFormDid),
            Ensure.that(didDocument.verificationMethod!![0].controller).contains(longFormDid),
            Ensure.that(didDocument.verificationMethod!![0].publicKeyJwk.crv!!).isEqualTo(curve.value),
            Ensure.that(didDocument.service!!.size).isGreaterThanOrEqualTo(1),
            Ensure.that(didDocument.service!![0].id).contains(longFormDid),
        )

        when (purpose) {
            Purpose.ASSERTION_METHOD -> {
                actor.attemptsTo(
                    Ensure.that(resolvedDid.didDocument!!.assertionMethod!!.size).isEqualTo(1),
                )
            }

            Purpose.AUTHENTICATION -> {
                actor.attemptsTo(
                    Ensure.that(didDocument.authentication!!.size).isEqualTo(1),
                )
            }

            Purpose.CAPABILITY_DELEGATION -> {
                actor.attemptsTo(
                    Ensure.that(didDocument.capabilityDelegation!!.size).isEqualTo(1),
                )
            }

            Purpose.CAPABILITY_INVOCATION -> {
                actor.attemptsTo(
                    Ensure.that(didDocument.capabilityInvocation!!.size).isEqualTo(1),
                )
            }

            Purpose.KEY_AGREEMENT -> {
                actor.attemptsTo(
                    Ensure.that(resolvedDid.didDocument!!.keyAgreement!!.size).isEqualTo(1),
                )
            }
        }
    }

    @Then("{actor} sees PRISM DID was not successfully created")
    fun theDidShouldNotBeRegisteredSuccessfully(actor: Actor) {
        val error = SerenityRest.lastResponse().get<ErrorResponse>()
        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_UNPROCESSABLE_ENTITY),
            Ensure.that(error.detail ?: "").isNotEmpty(),
        )
    }

    @Then("{actor} sees the list contains all created DIDs")
    fun seeTheListContainsAllCreatedDids(actor: Actor) {
        val expectedDids = actor.recall<List<String>>("createdDids")
        val managedDidList = SerenityRest.lastResponse().get<ManagedDIDPage>().contents!!
            .filter { it.status == "CREATED" }.map { it.longFormDid!! }
        actor.attemptsTo(
            Ensure.that(managedDidList).containsElementsFrom(expectedDids),
        )
    }

    private fun createPrismDidRequest(curve: Curve, purpose: Purpose): CreateManagedDidRequest =
        CreateManagedDidRequest(
            CreateManagedDidRequestDocumentTemplate(
                publicKeys = listOf(ManagedDIDKeyTemplate("auth-1", purpose, curve)),
                services = listOf(
                    Service("https://foo.bar.com", listOf("LinkedDomains"), JsonPrimitive("https://foo.bar.com/")),
                ),
            ),
        )
}
