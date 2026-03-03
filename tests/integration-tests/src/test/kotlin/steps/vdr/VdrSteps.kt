package steps.vdr

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import net.serenitybdd.core.Serenity
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.rest.SerenityRest
import org.apache.http.HttpStatus.SC_BAD_REQUEST
import org.apache.http.HttpStatus.SC_CREATED
import org.apache.http.HttpStatus.SC_NOT_FOUND
import org.apache.http.HttpStatus.SC_OK
import org.hyperledger.identus.apollo.utils.decodeHex
import org.hyperledger.identus.apollo.utils.toHexString
import org.assertj.core.api.Assertions.assertThat
import steps.vdr.Poll
import steps.vdr.VdrClient
import steps.vdr.VdrContext
import steps.vdr.VdrDriver
import steps.vdr.VdrLog
import java.net.URLEncoder

class VdrSteps {
    companion object {
        private const val NONE_VALUE = "<none>"
    }

    private fun ctx(actor: Actor): VdrContext = actor.recall("vdrCtx") ?: VdrContext()
    private fun saveCtx(actor: Actor, c: VdrContext) = actor.remember("vdrCtx", c)
    private fun recallString(actor: Actor, key: String): String? = runCatching { actor.recall<String>(key) }.getOrNull()

    @Given("{actor} has a VDR entry with value {} using {} driver")
    fun agentHasVdrEntry(actor: Actor, dataHex: String, driver: String) {
        val c = ctx(actor)
        val expectedDriver = VdrDriver.fromName(driver)
        if (c.entryId == null || c.payload?.toHexString() != dataHex || c.driver != expectedDriver) {
            agentCreatesVdrEntry(actor, dataHex, driver)
        }
    }

    @When("{actor} creates a VDR entry with value {} using {} driver")
    fun agentCreatesVdrEntry(actor: Actor, dataHex: String, driver: String) {
        val driverEnum = VdrDriver.fromName(driver)
        val data = dataHex.decodeHex()
        val didKeyId = recallString(actor, "didKeyId")
        val did = recallString(actor, "shortFormDid")
        VdrLog.request("create", "driver=$driver\nshortDid=${did ?: NONE_VALUE}\ndidKeyId=${didKeyId ?: NONE_VALUE}\nbytes=$dataHex")
        val client = VdrClient(actor)
        val resp = client.postEntry(data, driverEnum, didKeyId)
        val newCtx = ctx(actor)
            .copy(driver = driverEnum, didKeyId = didKeyId)
            .withUrls(resp.url)
            .withOperation(resp.operationId)
            .withPayload(data)
        saveCtx(actor, newCtx)
        actor.remember("vdrDriver", driver) // backward compat with other steps
        actor.remember("vdrUrl", resp.url)
        actor.remember("vdrEntryId", resp.url)
        actor.remember("vdrCurrentUrl", resp.url)
        actor.remember("vdrExpectedValue", dataHex) // reset expected payload for fresh entry
        Serenity.setSessionVariable("lastVdrUrl").to(resp.url)
        waitForPrismOperation(actor)
    }

    @When("{actor} updates the VDR entry with value {}")
    fun agentUpdateVdrEntry(actor: Actor, dataHex: String) {
        val c = ctx(actor)
        val url = c.currentUrl ?: c.entryId ?: recallString(actor, "vdrUrl")
        requireNotNull(url) { "Missing VDR url for update" }
        val data = dataHex.decodeHex()
        val didKeyId = c.didKeyId ?: recallString(actor, "didKeyId")
        VdrLog.request("update", "url=$url\ndidKeyId=${didKeyId ?: NONE_VALUE}\nbytes=$dataHex")
        val client = VdrClient(actor)
        val resp = client.putEntry(url, data, didKeyId)
        val newCtx = c.withUrls(resp.url ?: url).withOperation(resp.operationId).withPayload(data)
        saveCtx(actor, newCtx)
        actor.remember("vdrCurrentUrl", resp.url ?: url)
        actor.remember("vdrExpectedValue", dataHex)
        Serenity.setSessionVariable("lastVdrUrl").to(resp.url ?: url)
        waitForPrismOperation(actor)
    }

    @When("{actor} deletes the VDR entry")
    fun agentDeleteVdrEntry(actor: Actor) {
        val c = ctx(actor)
        val url = c.currentUrl ?: c.entryId ?: recallString(actor, "vdrUrl")
        requireNotNull(url) { "Missing VDR url for delete" }
        val client = VdrClient(actor)
        client.deleteEntry(url, c.didKeyId)
        val opId = SerenityRest.lastResponse().jsonPath().getString("operationId")
        saveCtx(actor, c.withOperation(opId))
        waitForPrismOperation(actor)
        if (c.driver == VdrDriver.PRISM_NODE || c.driver == VdrDriver.NEOPRISM) Thread.sleep(5_000) // small settle wait
    }

    @Then("{actor} shares the VDR URL with {actor}")
    fun actorShareVdrUrlTo(fromActor: Actor, toActor: Actor) {
        val c = ctx(fromActor)
        val url = c.entryId ?: recallString(fromActor, "vdrUrl")
        toActor.remember("vdrUrl", url)
        saveCtx(toActor, c.copy()) // shallow copy to share driver/ids
        fromActor.recall<String>("vdrDriver")?.let { toActor.remember("vdrDriver", it) }
    }

    @Then("{actor} uses the VDR URL to locate the data with value {}")
    fun agentResolveVdrEntry(actor: Actor, dataHex: String) {
        val c = ctx(actor)
        val vdrUrl = c.currentUrl
            ?: recallString(actor, "vdrCurrentUrl")
            ?: c.entryId
            ?: runCatching { SerenityRest.lastResponse().get<String>("url") }.getOrNull()
        requireNotNull(vdrUrl) { "VDR URL missing in actor/session context; cannot resolve entry" }
        val driver = c.driver
        // Prefer the last remembered expected value (set during create/update); fall back to the step arg.
        val expectedHex = actor.recall<String>("vdrExpectedValue") ?: dataHex
        val expectedBytes = expectedHex.decodeHex()
        saveCtx(actor, c.withPayload(expectedBytes))
        VdrLog.request("resolve", "url=$vdrUrl\ndriver=${driver.drid}")

        Serenity.recordReportData()
            .withTitle("VDR expected value snapshot")
            .andContents("expectedHex=$expectedHex\nstepArg=$dataHex")

        if (driver == VdrDriver.PRISM_NODE || driver == VdrDriver.NEOPRISM) waitForPrismOperation(actor)
        actor.remember("vdrExpectedValue", expectedHex)
        val client = VdrClient(actor)
        val status = Poll.until(
            action = { client.getEntry(vdrUrl) },
            condition = { it == SC_OK }
        )
        VdrLog.response(
            "resolve",
            "status=${SerenityRest.lastResponse().statusCode}\nbody=${SerenityRest.lastResponse().body.asString()}"
        )
        actor.attemptsTo(Ensure.that(status).isEqualTo(SC_OK))
        val resolvedVdrData = client.getEntryBytes(vdrUrl).toHexString()
        saveCtx(actor, c.withPayload(expectedBytes))
        Serenity.recordReportData()
            .withTitle("VDR resolve comparison")
            .andContents("url=$vdrUrl\nexpected=$expectedHex\nactual=$resolvedVdrData")
        println("VDR RESOLVE debug: expected=$expectedHex actual=$resolvedVdrData url=$vdrUrl")
        actor.attemptsTo(Ensure.that(resolvedVdrData).isEqualTo(expectedHex))
    }

    @Then("{actor} could not resolve the VDR URL")
    fun agentResolveVdrEntry(actor: Actor) {
        val c = ctx(actor)
        val vdrUrl = c.currentUrl ?: c.entryId ?: recallString(actor, "vdrUrl")
        requireNotNull(vdrUrl) { "VDR URL missing in actor/session context; cannot check unresolved entry" }
        val driver = c.driver
        val maxAttempts = if (driver == VdrDriver.PRISM_NODE || driver == VdrDriver.NEOPRISM) 24 else 12
        if (driver == VdrDriver.PRISM_NODE || driver == VdrDriver.NEOPRISM) waitForPrismOperation(actor)
        val client = VdrClient(actor)
        val status = Poll.until(
            timeout = java.time.Duration.ofSeconds((maxAttempts * 5).toLong()),
            interval = java.time.Duration.ofSeconds(5),
            action = { client.getEntry(vdrUrl) },
            condition = { it == SC_NOT_FOUND || it == SC_BAD_REQUEST }
        )
        assertThat(status).isEqualTo(SC_NOT_FOUND)
    }

    private fun waitForPrismOperation(actor: Actor) {
        val c = ctx(actor)
        if (c.driver != VdrDriver.PRISM_NODE && c.driver != VdrDriver.NEOPRISM) return
        val opId = c.operationId ?: return
        val client = VdrClient(actor)
        val status = Poll.until(action = { client.getOperationStatus(opId) }) { it == "CONFIRMED_AND_APPLIED" }
        actor.attemptsTo(Ensure.that(status).isEqualTo("CONFIRMED_AND_APPLIED"))
    }
}
