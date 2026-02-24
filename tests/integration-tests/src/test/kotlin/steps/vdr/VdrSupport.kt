package steps.vdr

import net.serenitybdd.core.Serenity
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.rest.abilities.CallAnApi
import net.serenitybdd.rest.SerenityRest
import org.apache.http.HttpStatus
import io.restassured.specification.RequestSpecification
import java.time.Duration

enum class VdrDriver(
    val drid: String,
    val drf: String,
    val version: String,
    val mutable: Boolean = true
) {
    PRISM_NODE("prism-node", "prism", "1.0.0"),
    NEOPRISM("neoprism", "prism", "1.0.0"),
    SCALA_DID("scala-did", "prism", "1.0.0"),
    MEMORY("memory", "memory", "0.1.0"),
    DATABASE("database", "database", "0.1.0");

    fun applyParams(spec: io.restassured.specification.RequestSpecification): Unit {
        spec.queryParam("drf", drf)
        spec.queryParam("drid", drid)
        spec.queryParam("drv", version)
        spec.queryParam("m", mutable)
    }

    companion object {
        fun fromName(name: String): VdrDriver =
            entries.firstOrNull { it.drid == name }
                ?: throw IllegalArgumentException("Unknown VDR driver: $name")
    }
}

data class VdrContext(
    val entryId: String? = null,
    val currentUrl: String? = null,
    val driver: VdrDriver = VdrDriver.MEMORY,
    val operationId: String? = null,
    val payload: ByteArray? = null,
    val didKeyId: String? = null
) {
    fun withUrls(url: String?) = copy(
        entryId = url ?: entryId,
        currentUrl = url ?: currentUrl
    )

    fun withOperation(opId: String?) = copy(operationId = opId ?: operationId)

    fun withPayload(bytes: ByteArray) = copy(payload = bytes)
}

data class VdrOpResponse(val url: String?, val operationId: String?)

object VdrLog {
    fun request(title: String, body: String) =
        Serenity.recordReportData().withTitle("VDR $title request").andContents(body)

    fun response(title: String, body: String) =
        Serenity.recordReportData().withTitle("VDR $title response").andContents(body)
}

object Poll {
    fun <T> until(
        timeout: Duration = Duration.ofSeconds(60),
        interval: Duration = Duration.ofSeconds(5),
        action: () -> T,
        condition: (T) -> Boolean
    ): T {
        val start = System.nanoTime()
        var result = action()
        while (!condition(result) && Duration.ofNanos(System.nanoTime() - start) < timeout) {
            Thread.sleep(interval.toMillis())
            result = action()
        }
        return result
    }
}

/**
 * Minimal helper around SerenityRest for VDR scenarios.
 */
class VdrClient(private val actor: Actor) {

    private fun authSpec(): RequestSpecification {
        val spec = SerenityRest.given()
        actor.recall<String>("baseUrl")?.let { spec.baseUri(it) }
        actor.recall<String>("BEARER_TOKEN")?.let { token -> spec.header("Authorization", "Bearer $token") }
        actor.recall<String>("AUTH_KEY")?.let { key ->
            val headerName = actor.recall<String>("AUTH_HEADER") ?: "apikey"
            spec.header(headerName, key)
        }
        return spec
    }

    private fun ensureApiAbility() {
        val baseUrl = actor.recall<String>("baseUrl")
        val hasAbility = try {
            actor.abilityTo(CallAnApi::class.java)
            true
        } catch (_: Exception) {
            false
        }
        if (baseUrl != null && !hasAbility) actor.can(CallAnApi.at(baseUrl))
    }

    fun postEntry(body: ByteArray, driver: VdrDriver, didKeyId: String?): VdrOpResponse {
        ensureApiAbility()
        authSpec()
            .contentType("application/octet-stream")
            .body(body)
            .also { driver.applyParams(it) }
            .also { didKeyId?.let { key -> it.queryParam("didKeyId", key) } }
            .post("/vdr/entries")

        val status = SerenityRest.lastResponse().statusCode
        VdrLog.response("create", "status=$status\nbody=${SerenityRest.lastResponse().body.asString()}")
        if (status != HttpStatus.SC_CREATED) {
            throw AssertionError("Expected 201 but got $status")
        }
        return VdrOpResponse(
            SerenityRest.lastResponse().jsonPath().getString("url"),
            SerenityRest.lastResponse().jsonPath().getString("operationId")
        )
    }

    fun putEntry(url: String, body: ByteArray, didKeyId: String?): VdrOpResponse {
        ensureApiAbility()
        authSpec()
            .contentType("application/octet-stream")
            .queryParam("url", url)
            .also { didKeyId?.let { key -> it.queryParam("didKeyId", key) } }
            .body(body)
            .put("/vdr/entries")

        val status = SerenityRest.lastResponse().statusCode
        if (status != HttpStatus.SC_OK) {
            throw AssertionError("Expected 200 but got $status")
        }
        VdrLog.response("update", "status=$status\nbody=${SerenityRest.lastResponse().body.asString()}")
        return VdrOpResponse(
            SerenityRest.lastResponse().jsonPath().getString("url"),
            SerenityRest.lastResponse().jsonPath().getString("operationId")
        )
    }

    fun deleteEntry(url: String, didKeyId: String?) {
        ensureApiAbility()
        authSpec()
            .queryParam("url", url)
            .also { didKeyId?.let { key -> it.queryParam("didKeyId", key) } }
            .delete("/vdr/entries")
        val status = SerenityRest.lastResponse().statusCode
        if (status != HttpStatus.SC_OK) {
            throw AssertionError("Expected 200 but got $status")
        }
    }

    fun getEntry(url: String): Int {
        ensureApiAbility()
        authSpec()
            .header("Accept", "application/octet-stream")
            .queryParam("url", url)
            .get("/vdr/entries")
        return SerenityRest.lastResponse().statusCode
    }

    fun getEntryBytes(url: String): ByteArray =
        SerenityRest.lastResponse().body.asByteArray()

    fun getOperationStatus(opId: String): String {
        ensureApiAbility()
        authSpec().get("/vdr/operations/$opId")
        return SerenityRest.lastResponse().jsonPath().getString("status")
    }
}
