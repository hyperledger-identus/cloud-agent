package steps.vdr

import interactions.Delete
import interactions.Get
import interactions.Post
import interactions.Put
import interactions.rawBytesBody
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.http.HttpStatus.SC_NOT_FOUND
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.apollo.utils.decodeHex
import org.hyperledger.identus.apollo.utils.toHexString

class VdrSteps {
    @Given("{actor} has a VDR entry with value {}")
    fun agentHasVdrEntry(actor: Actor, dataHex: String) {
        val vdrUrl = actor.recall<String>("vdrUrl")
        val vdrData = actor.recall<ByteArray>("vdrData")
        if (vdrUrl == null || vdrData.toHexString() != dataHex) {
            agentCreatesVdrEntry(actor, dataHex)
        }
    }

    @When("{actor} creates a VDR entry with value {}")
    fun agentCreatesVdrEntry(actor: Actor, dataHex: String) {
        val data = dataHex.decodeHex()
        actor.attemptsTo(
            Post.to("/vdr/entries")
                .rawBytesBody(data)
                .with {
                    it.queryParam("drf", "memory")
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
        )

        val url = SerenityRest.lastResponse().get<String>("url")
        actor.remember("vdrUrl", url)
        actor.remember("vdrData", data)
    }

    @When("{actor} updates the VDR entry with value {}")
    fun agentUpdateVdrEntry(actor: Actor, dataHex: String) {
        val url = actor.recall<String>("vdrUrl")
        val data = dataHex.decodeHex()
        actor.attemptsTo(
            Put.to("/vdr/entries")
                .rawBytesBody(data)
                .with {
                    it.queryParam("url", url)
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )
        actor.remember("vdrData", data)
    }

    @When("{actor} deletes the VDR entry")
    fun agentDeleteVdrEntry(actor: Actor) {
        val url = actor.recall<String>("vdrUrl")
        actor.attemptsTo(
            Delete.from("/vdr/entries")
                .with {
                    it.queryParam("url", url)
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )
        actor.forget<ByteArray>("vdrData" )
    }

    @Then("{actor} shares the VDR URL with {actor}")
    fun actorShareVdrUrlTo(fromActor: Actor, toActor: Actor) {
        val url = fromActor.recall<String>("vdrUrl")
        toActor.remember("vdrUrl", url)
    }

    @Then("{actor} uses the VDR URL to locate the data with value {}")
    fun agentResolveVdrEntry(actor: Actor, dataHex: String) {
        val vdrUrl = actor.recall<String>("vdrUrl")
        val vdrData = dataHex
        actor.attemptsTo(
            Get.resource("/vdr/entries")
                .with {
                    it.queryParam("url", vdrUrl)
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK)
        )

        val resolvedVdrData = SerenityRest.lastResponse().body.asByteArray().toHexString()
        actor.attemptsTo(
            Ensure.that(vdrData).isEqualTo(resolvedVdrData)
        )
    }

    @Then("{actor} could not resolve the VDR URL")
    fun agentResolveVdrEntry(actor: Actor) {
        val vdrUrl = actor.recall<String>("vdrUrl")
        actor.attemptsTo(
            Get.resource("/vdr/entries")
                .with {
                    it.queryParam("url", vdrUrl)
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_NOT_FOUND)
        )
    }
}