package interactions

import io.restassured.specification.RequestSpecification
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.rest.interactions.RestInteraction

abstract class AuthRestInteraction : RestInteraction() {

    fun <T : Actor?> specWithAuthHeaders(actor: T): RequestSpecification {
        // Prefer the RestInteraction base spec (may carry body/query etc); fall back to a fresh one.
        val base = rest()
        val spec = base ?: SerenityRest.given()

        actor?.recall<String>("baseUrl")?.let { spec.baseUri(it) }

        actor?.recall<String>("BEARER_TOKEN")?.let {
            spec.header("Authorization", "Bearer $it")
        }

        actor?.recall<String>("AUTH_KEY")?.let {
            val headerName = actor.recall<String>("AUTH_HEADER") ?: "apikey"
            spec.header(headerName, it)
        }

        return spec
    }
}
