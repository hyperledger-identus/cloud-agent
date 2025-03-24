package steps.schemas

import common.*
import common.CredentialSchema.STUDENT_SCHEMA
import common.errors.SchemaTemplateError
import interactions.*
import io.cucumber.java.en.*
import io.iohk.atala.automation.extensions.get
import io.iohk.atala.automation.serenity.ensure.Ensure
import models.JsonSchema
import net.serenitybdd.rest.SerenityRest
import net.serenitybdd.screenplay.Actor
import org.apache.http.HttpStatus.*
import org.hyperledger.identus.client.models.CredentialSchemaInput
import org.hyperledger.identus.client.models.CredentialSchemaResponse
import java.util.UUID

class CredentialSchemasSteps {

    @Given("{actor} has a published '{}' schema")
    fun agentHasAPublishedSchema(agent: Actor, schema: CredentialSchema) {
        if (agent.recallAll().containsKey(schema.name)) {
            return
        }
        agentCreatesANewCredentialSchema(agent, schema)
    }

    @When("{actor} creates a new credential '{}' schema")
    fun agentCreatesANewCredentialSchema(actor: Actor, schema: CredentialSchema) {
        actor.attemptsTo(
            Post.to("/schema-registry/schemas").body(
                schema.credentialSchema.copy(author = actor.recall("shortFormDid")),
            ),
        )
        actor.remember(schema.name, SerenityRest.lastResponse().get<String>("guid"))
    }

    @When("{actor} creates a schema containing '{}' issue")
    fun agentCreatesASchemaContainingIssue(actor: Actor, schema: SchemaTemplateError) {
        actor.attemptsTo(Post.to("/schema-registry/schemas").body(schema.schema(actor)))
    }

    @Then("{actor} sees new credential schema is available")
    fun newCredentialSchemaIsAvailable(actor: Actor) {
        val credentialSchema = SerenityRest.lastResponse().get<CredentialSchemaResponse>()
        val jsonSchema = SerenityRest.lastResponse().get<JsonSchema>("schema")

        actor.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
            Ensure.that(credentialSchema.guid).isNotNull(),
            Ensure.that(credentialSchema.id).isNotNull(),
            Ensure.that(credentialSchema.longId!!).isNotNull(),
            Ensure.that(credentialSchema.authored).isNotNull(),
            Ensure.that(credentialSchema.kind).isEqualTo("CredentialSchema"),
            Ensure.that(credentialSchema.name).contains(STUDENT_SCHEMA.credentialSchema.name),
            Ensure.that(credentialSchema.description).contains(STUDENT_SCHEMA.credentialSchema.description!!),
            Ensure.that(credentialSchema.version).contains(STUDENT_SCHEMA.credentialSchema.version),
            Ensure.that(credentialSchema.type).isEqualTo(STUDENT_SCHEMA.credentialSchemaType),
            Ensure.that(credentialSchema.tags!!)
                .containsExactlyInAnyOrderElementsFrom(STUDENT_SCHEMA.credentialSchema.tags!!),
            Ensure.that(jsonSchema.toString()).isEqualTo(STUDENT_SCHEMA.schema.toString()),
        )
    }

    @When("{actor} creates {int} new schemas")
    fun acmeCreatesMultipleSchemas(actor: Actor, numberOfSchemas: Int) {
        val createdSchemas: MutableList<CredentialSchemaResponse> = mutableListOf()
        repeat(numberOfSchemas) { i: Int ->
            val credentialSchemaInput = CredentialSchemaInput(
                author = actor.recall("shortFormDid"),
                name = "${UUID.randomUUID()} $i",
                description = "Simple student credentials schema",
                type = STUDENT_SCHEMA.credentialSchemaType,
                schema = STUDENT_SCHEMA.schema,
                tags = listOf("school", "students"),
                version = "1.0.0",
            )
            actor.attemptsTo(
                Post.to("/schema-registry/schemas").body(credentialSchemaInput),
                Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_CREATED),
            )
            createdSchemas.add(SerenityRest.lastResponse().get<CredentialSchemaResponse>())
        }
        actor.remember("createdSchemas", createdSchemas)
    }

    @Then("{actor} can access all of them one by one")
    fun theyCanBeAccessedWithPagination(actor: Actor) {
        actor.recall<List<CredentialSchemaResponse>>("createdSchemas").forEach { schema ->
            actor.attemptsTo(
                Get.resource("/schema-registry/schemas/${schema.guid}"),
                Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_OK),
            )
        }
    }

    @Then("{actor} should see the schema creation failed")
    fun schemaCreationShouldFail(agent: Actor) {
        agent.attemptsTo(
            Ensure.thatTheLastResponse().statusCode().isEqualTo(SC_BAD_REQUEST),
        )
    }
}
