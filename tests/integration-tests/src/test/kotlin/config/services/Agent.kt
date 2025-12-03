package config.services

import com.sksamuel.hoplite.ConfigAlias
import config.VaultAuthType
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.*

data class Agent(
    val version: String,
    @ConfigAlias("http_port") val httpPort: Int,
    @ConfigAlias("didcomm_port") val didcommPort: Int,
    @ConfigAlias("didcomm_service_url") val didcommServiceUrl: String?,
    @ConfigAlias("rest_service_url") val restServiceUrl: String?,
    @ConfigAlias("auth_enabled") val authEnabled: Boolean,
    @ConfigAlias("prism_node") val prismNode: VerifiableDataRegistry?,
    val keycloak: Keycloak?,
    val vault: Vault?,
) : ServiceBase() {

    override val logServices = listOf("identus-cloud-agent")
    override val container: ComposeContainer

    init {
        val env = mutableMapOf(
            "AGENT_VERSION" to version,
            "API_KEY_ENABLED" to authEnabled.toString(),
            "AGENT_DIDCOMM_PORT" to didcommPort.toString(),
            "DIDCOMM_SERVICE_URL" to (didcommServiceUrl ?: "http://host.docker.internal:$didcommPort"),
            "AGENT_HTTP_PORT" to httpPort.toString(),
            "REST_SERVICE_URL" to (restServiceUrl ?: "http://host.docker.internal:$httpPort"),
            "PRISM_NODE_PORT" to (prismNode?.httpPort?.toString() ?: ""),
            "SECRET_STORAGE_BACKEND" to if (vault != null) "vault" else "postgres",
            // FIXME: hardcode port 10001 just to avoid invalid URL 'http://host.docker.internal:'
            "VAULT_HTTP_PORT" to (vault?.httpPort?.toString() ?: "10001"),
            "KEYCLOAK_ENABLED" to (keycloak != null).toString(),
            // FIXME: hardcode port 10002 just to avoid invalid URL 'http://host.docker.internal:'
            "KEYCLOAK_HTTP_PORT" to (keycloak?.httpPort?.toString() ?: "10002"),
            "KEYCLOAK_REALM" to (keycloak?.realm ?: ""),
            "KEYCLOAK_CLIENT_ID" to (keycloak?.clientId ?: ""),
            "KEYCLOAK_CLIENT_SECRET" to (keycloak?.clientSecret ?: ""),
            "POLLUX_STATUS_LIST_REGISTRY_PUBLIC_URL" to "http://host.docker.internal:$httpPort",
        )

        // setup token authentication
        if (vault?.authType == VaultAuthType.TOKEN) {
            env["VAULT_TOKEN"] = "root"
        } else {
            env["VAULT_APPROLE_ROLE_ID"] = "agent"
            env["VAULT_APPROLE_SECRET_ID"] = "agent-secret"
        }

        container = ComposeContainer(File("src/test/resources/containers/agent.yml"))
            .withEnv(env)
            .waitingFor("identus-cloud-agent", Wait.forHealthcheck())
    }
}
