package steps.did

import interactions.Post
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.matchers.RestAssuredJsonProperty
import io.iohk.atala.automation.serenity.ensure.Ensure
import io.iohk.atala.automation.serenity.interactions.PollingWait
import io.iohk.atala.automation.serenity.questions.HttpRequest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus
import org.hyperledger.identus.client.models.DIDOperationResponse

class DeactivateDidSteps {

    @When("{actor} deactivates PRISM DID")
    fun actorIssuesDeactivateDidOperation(actor: Actor) {
        actor.attemptsTo(
            Post.to("/did-registrar/dids/${actor.recall<String>("shortFormDid")}/deactivations"),
        )

        val didOperationResponse = SerenityRest.lastResponse().get<DIDOperationResponse>()

        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(HttpStatus.SC_ACCEPTED),
            Ensure.that(didOperationResponse.scheduledOperation.didRef).isNotEmpty(),
            Ensure.that(didOperationResponse.scheduledOperation.id).isNotEmpty(),
        )
        actor.forget<String>("hasPublishedDid")
        val deactivatedDid = actor.forget<String>("shortFormDid")
        actor.forget<String>("longFormDid")
        actor.remember("deactivatedDid", deactivatedDid)
    }

    @Then("{actor} sees that PRISM DID is successfully deactivated")
    fun actorSeesThatPrismDidIsSuccessfullyDeactivated(actor: Actor) {
        val deactivatedDid = actor.recall<String>("deactivatedDid")
        actor.attemptsTo(
            PollingWait.with(5.minutes, 500.milliseconds).until(
                HttpRequest.get("/dids/$deactivatedDid"),
                RestAssuredJsonProperty.toBe("didDocumentMetadata.deactivated", "true"),
            ),
        )
    }
}
