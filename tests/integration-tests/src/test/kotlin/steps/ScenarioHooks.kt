package steps

import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import net.serenitybdd.screenplay.Actor
import net.serenitybdd.screenplay.actors.OnStage

/**
 * Scenario-level hooks to ensure test isolation by clearing actor memory between scenarios.
 * This prevents data leakage when running individual scenarios in isolation.
 */
class ScenarioHooks {

    /**
     * Configuration keys that should be preserved between scenarios as they are set up
     * during the initial actor initialization and are required for basic functionality.
     */
    private val preservedKeys = setOf(
        "BEARER_TOKEN",
        "AUTH_KEY", 
        "AUTH_HEADER",
        "baseUrl",
        "webhookUrl",
        "WEBHOOK_ID",
        "OID4VCI_AUTH_SERVER_URL",
        "OID4VCI_AUTH_SERVER_CLIENT_ID", 
        "OID4VCI_AUTH_SERVER_CLIENT_SECRET"
    )

    @Before
    fun beforeScenario(scenario: Scenario) {
        // Current implementation preserves the stage setup from @BeforeAll
        // In the future, if needed, we could add additional scenario-specific setup here
    }

    @After
    fun afterScenario(scenario: Scenario) {
        try {
            // Clear scenario-specific data from all actors while preserving configuration
            clearActorMemories()
        } catch (e: Exception) {
            // Log the error but don't fail the test - this is cleanup
            println("Warning: Failed to clear actor memories after scenario '${scenario.name}': ${e.message}")
        }
    }

    /**
     * Clears scenario-specific data from all actors while preserving essential configuration.
     * This prevents test scenarios from leaking data to each other when run in isolation.
     */
    private fun clearActorMemories() {
        // Clear memory for all known actor roles
        val knownActorRoles = listOf("Issuer", "Holder", "Verifier", "Admin", "Alice", "Bob")
        
        knownActorRoles.forEach { roleName ->
            try {
                val actor = OnStage.theActorCalled(roleName)
                clearActorMemory(actor)
            } catch (e: Exception) {
                // Actor might not exist for this test scenario, which is fine
                // We just want to clean up actors that do exist
            }
        }
    }

    /**
     * Clears an individual actor's memory while preserving essential configuration keys.
     */
    private fun clearActorMemory(actor: Actor) {
        // Clear all scenario-specific keys that commonly cause data leakage
        // Based on analysis of actual remember() calls in the test codebase
        val scenarioSpecificKeys = setOf(
            // Connection-related keys
            "connection",
            "connectionId", 
            "connection-with-Issuer",
            "connection-with-Holder", 
            "connection-with-Verifier",
            "connection-with-Admin",
            
            // DID-related keys
            "customDid",
            "deactivatedDid",
            "didPurpose",
            "didVerification",
            "hasPublishedDid",
            "longFormDid",
            "shortFormDid",
            "currentDID",
            "newDidKeyId",
            "createdDids",
            
            // Schema-related keys
            "anoncredsSchema",
            "createdSchemas",
            "currentSchema",
            
            // Credential-related keys
            "anoncredsCredentialDefinition",
            "credentialConfiguration",
            "credentialConfigurationId", 
            "credentialRecord",
            "currentAssertionKey",
            "currentClaims",
            "currentCredentialType",
            "recordId",
            
            // Service and update-related keys
            "newServiceId",
            "newServiceUrl",
            "updatedPolicyInput",
            
            // OID4VCI-related keys
            "eudiAuthorizedRequest",
            "eudiCredentialOffer",
            "eudiIssuedCredential",
            "requestedCredential",
            
            // Verification and policy keys
            "policy",
            "checks",
            "encodedStatusList",
            
            // VDR keys
            "vdrData",
            "vdrDriver", 
            "vdrUrl",
            
            // API state keys
            "currentAPI",
            "number"
        )

        // Also clear connection keys with dynamic names (connection-with-{actorName})
        val dynamicConnectionKeys = setOf(
            "connection-with-Issuer",
            "connection-with-Holder",
            "connection-with-Verifier", 
            "connection-with-Admin",
            "connection-with-Alice",
            "connection-with-Bob"
        )

        // Clear all scenario-specific keys
        (scenarioSpecificKeys + dynamicConnectionKeys).forEach { key ->
            try {
                actor.forget<Any>(key)
            } catch (e: Exception) {
                // Key might not exist or might not be the correct type, which is fine
                // We just want to ensure cleanup happens without breaking anything
            }
        }
    }
}