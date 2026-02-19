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
import net.serenitybdd.core.Serenity
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.rest.abilities.CallAnApi
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.http.HttpStatus.SC_NOT_FOUND
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.apollo.utils.decodeHex
import org.hyperledger.identus.apollo.utils.toHexString
import java.net.URLEncoder
import org.assertj.core.api.Assertions.assertThat

class VdrSteps {
    private fun ensureApiAbility(actor: Actor) {
        val baseUrl = actor.recall<String>("baseUrl")
        val hasAbility = try {
            actor.abilityTo(CallAnApi::class.java)
            true
        } catch (e: Exception) {
            false
        }
        if (baseUrl != null && !hasAbility) actor.can(CallAnApi.at(baseUrl))
    }

    @Given("{actor} has a VDR entry with value {} using {} driver")
    fun agentHasVdrEntry(actor: Actor, dataHex: String, driver: String) {
        val vdrUrl = actor.recall<String>("vdrUrl")
        val vdrData = actor.recall<ByteArray>("vdrData")
        val vdrDriver = actor.recall<String>("vdrDriver")
        if (vdrUrl == null
            || vdrData == null
            || vdrDriver == null
            || vdrData.toHexString() != dataHex
            || vdrDriver != driver
        ) {
            agentCreatesVdrEntry(actor, dataHex, driver)
        }
    }

    @When("{actor} creates a VDR entry with value {} using {} driver")
    fun agentCreatesVdrEntry(actor: Actor, dataHex: String, driver: String) {
        ensureApiAbility(actor)
        val data = dataHex.decodeHex()
        val didKeyId = actor.recall<String>("didKeyId")
        val did = actor.recall<String>("shortFormDid")
        Serenity.recordReportData()
            .withTitle("VDR create request")
            .andContents(
                "driver=$driver\nshortDid=${did ?: "<none>"}\ndidKeyId=${didKeyId ?: "<none>"}\nbytes=$dataHex"
            )
        actor.attemptsTo(
            Post.to("/vdr/entries")
                .rawBytesBody(data)
                .with {
                    it.contentType("application/octet-stream")
                    applyDriverParams(it, driver)
                    didKeyId?.let { keyId -> it.queryParam("didKeyId", keyId) }
                    it
                }
        )

        val status = SerenityRest.lastResponse().statusCode
        Serenity.recordReportData()
            .withTitle("VDR create response")
            .andContents(
                "status=$status\nbody=${SerenityRest.lastResponse().body.asString()}"
            )
        if (status != SC_CREATED) {
            actor.attemptsTo(Ensure.that(status).isEqualTo(SC_CREATED))
        }

        val url = SerenityRest.lastResponse().get<String>("url")
        SerenityRest.lastResponse().jsonPath().get<String>("operationId")?.let { actor.remember("vdrOperationId", it) }
        actor.remember("vdrUrl", url)            // initial immutable hash (acts as entry id)
        actor.remember("vdrEntryId", url)
        actor.remember("vdrCurrentUrl", url)     // current version hash to use for resolution expectations
        actor.remember("vdrDriver", driver)
        actor.remember("vdrData", data)
        Serenity.setSessionVariable("lastVdrUrl").to(url)
        waitForPrismOperation(actor)

    }

    @When("{actor} updates the VDR entry with value {}")
    fun agentUpdateVdrEntry(actor: Actor, dataHex: String) {
        ensureApiAbility(actor)
        val url = actor.recall<String>("vdrUrl")
        val data = dataHex.decodeHex()
        val didKeyId = actor.recall<String>("didKeyId")
        Serenity.recordReportData()
            .withTitle("VDR update request")
            .andContents(
                "url=$url\ndidKeyId=${didKeyId ?: "<none>"}\nbytes=$dataHex"
            )
        actor.attemptsTo(
            Put.to("/vdr/entries")
                .rawBytesBody(data)
                .with {
                    it.contentType("application/octet-stream")
                    it.queryParam("url", url)
                    didKeyId?.let { keyId -> it.queryParam("didKeyId", keyId) }
                    it
                },
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
        )
        SerenityRest.lastResponse().jsonPath().get<String>("operationId")?.let { actor.remember("vdrOperationId", it) }
        // Keep the original entry id for resolution; store the latest hash separately if returned
        SerenityRest.lastResponse().jsonPath().get<String>("url")?.let {
            actor.remember("vdrCurrentUrl", it)
            Serenity.setSessionVariable("lastVdrUrl").to(it)
        }
        actor.remember("vdrData", data)
        waitForPrismOperation(actor)
    }

    @When("{actor} deletes the VDR entry")
    fun agentDeleteVdrEntry(actor: Actor) {
        ensureApiAbility(actor)
        val url = actor.recall<String>("vdrCurrentUrl") ?: actor.recall<String>("vdrEntryId") ?: actor.recall<String>("vdrUrl")
        actor.attemptsTo(
            Delete.from("/vdr/entries")
                .with {
                    it.queryParam("url", url)
                    actor.recall<String>("didKeyId")?.let { keyId ->
                        it.queryParam("didKeyId", keyId)
                    }
                    it
                }
        )
        val status = SerenityRest.lastResponse().statusCode
        actor.attemptsTo(Ensure.that(status).isEqualTo(SC_OK))
        val bodyStr = SerenityRest.lastResponse().body?.asString()
        if (!bodyStr.isNullOrBlank() && SerenityRest.lastResponse().contentType?.contains("json", ignoreCase = true) == true) {
            SerenityRest.lastResponse().jsonPath().get<String>("operationId")?.let { actor.remember("vdrOperationId", it) }
        }
        waitForPrismOperation(actor)
        if (actor.recall<String>("vdrDriver") == "prism-node") {
            // small settle wait for head/index update
            Thread.sleep(5_000)
        }
    }

    @Then("{actor} shares the VDR URL with {actor}")
    fun actorShareVdrUrlTo(fromActor: Actor, toActor: Actor) {
        val url = fromActor.recall<String>("vdrUrl") ?: fromActor.recall<String>("vdrEntryId")
        toActor.remember("vdrUrl", url)
        fromActor.recall<String>("vdrDriver")?.let { toActor.remember("vdrDriver", it) }
    }

    @Then("{actor} uses the VDR URL to locate the data with value {}")
    fun agentResolveVdrEntry(actor: Actor, dataHex: String) {
        ensureApiAbility(actor)
        // Always use the canonical entry id/hash stored on creation to avoid cross-scenario leakage.
        val vdrUrl = actor.recall<String>("vdrCurrentUrl")
            ?: actor.recall<String>("vdrEntryId")
            ?: actor.recall<String>("vdrUrl")
            ?: SerenityRest.lastResponse().get<String>("url")
                ?.also {
                    actor.remember("vdrUrl", it)
                    actor.remember("vdrEntryId", it)
                    actor.remember("vdrCurrentUrl", it)
                }

        requireNotNull(vdrUrl) { "VDR URL missing in actor/session context; cannot resolve entry" }
        val vdrDriver = actor.recall<String>("vdrDriver")
        // The step argument is the source of truth for expected payload; store it for subsequent steps.
        val vdrData = dataHex
        actor.remember("vdrData", dataHex.decodeHex())
        Serenity.recordReportData()
            .withTitle("VDR resolve request")
            .andContents("url=$vdrUrl\ndriver=${vdrDriver ?: "<none>"}")

        if (vdrDriver == "prism-node") waitForPrismOperation(actor)
        var attempts = 0
        var status = -1
        while (attempts < 12 && status != SC_OK) {
            actor.attemptsTo(
                Get.resource("/vdr/entries")
                    .with {
                        it.header("Accept", "application/octet-stream")
                        it.queryParam("url", vdrUrl)
                        it
                    },
            )
            status = SerenityRest.lastResponse().statusCode
            if (status == SC_OK) break
            Thread.sleep(5_000)
            attempts++
        }

        Serenity.recordReportData()
            .withTitle("VDR resolve response")
            .andContents(
                "status=${SerenityRest.lastResponse().statusCode}\n" +
                    "body=${SerenityRest.lastResponse().body.asString()}\n" +
                    "expected=$vdrData"
            )

        actor.attemptsTo(Ensure.that(status).isEqualTo(SC_OK))
        val resolvedVdrData = SerenityRest.lastResponse().body.asByteArray().toHexString()
        Serenity.recordReportData()
            .withTitle("VDR resolve comparison")
            .andContents("url=$vdrUrl\nexpected=$vdrData\nactual=$resolvedVdrData")
        actor.attemptsTo(Ensure.that(vdrData).isEqualTo(resolvedVdrData))
    }

    @Then("{actor} could not resolve the VDR URL")
    fun agentResolveVdrEntry(actor: Actor) {
        ensureApiAbility(actor)
        // Prefer the latest known hash (after updates) to reflect the current head.
        val vdrUrl = actor.recall<String>("vdrCurrentUrl")
            ?: actor.recall<String>("vdrUrl")
        val vdrDriver = actor.recall<String>("vdrDriver")
        val maxAttempts = if (vdrDriver == "prism-node") 24 else 12
        // for prism-node, wait for operation to apply before checking not-found
        if (vdrDriver == "prism-node") waitForPrismOperation(actor)
        var attempts = 0
        var status: Int
        do {
            actor.attemptsTo(
                Get.resource("/vdr/entries")
                    .with {
                        it.header("Accept", "application/octet-stream")
                        it.queryParam("url", vdrUrl)
                        it
                    }
            )
            status = SerenityRest.lastResponse().statusCode
            // Only break early on definitive not-found or malformed; keep polling on 200 for prism-node to allow deactivation to propagate
            if (status == SC_NOT_FOUND || status == SC_BAD_REQUEST) break
            Thread.sleep(5_000)
            attempts++
        } while (attempts < maxAttempts)

        assertThat(status).isEqualTo(SC_NOT_FOUND)
    }

    private fun waitForPrismOperation(actor: Actor) {
        val driver = actor.recall<String>("vdrDriver")
        if (driver != "prism-node") return
        val opId = actor.recall<String>("vdrOperationId") ?: return
        var opAttempts = 0
        var confirmed = false
        while (opAttempts < 12 && !confirmed) {
            actor.attemptsTo(Get.resource("/vdr/operations/$opId"))
            val statusName = SerenityRest.lastResponse().jsonPath().getString("status")
            confirmed = statusName == "CONFIRMED_AND_APPLIED"
            if (!confirmed) Thread.sleep(5_000)
            opAttempts++
        }
        actor.attemptsTo(Ensure.that(confirmed).isTrue())
    }

    private fun applyDriverParams(spec: io.restassured.specification.RequestSpecification, driver: String) {
        when (driver) {
            "prism-node", "scala-did", "neoprism" -> {
                spec.queryParam("drf", "prism")
                spec.queryParam("drv", "1.0.0")
                spec.queryParam("m", true)
            }
            "memory", "database" -> {
                spec.queryParam("drf", driver)
                spec.queryParam("drv", "0.1.0")
                spec.queryParam("m", true)
            }
        }
        spec.queryParam("drid", driver)
    }
}
