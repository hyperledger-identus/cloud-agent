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
    val neoprism: NeoPrism?,
    val keycloak: Keycloak?,
    val vault: Vault?,
    //TODO: leave only one config alias
    @ConfigAlias("vdr_driver") @ConfigAlias("vdr_driver_default") val vdrDriverDefault: String? = null,
) : ServiceBase() {

    override val logServices = listOf("identus-cloud-agent")
    override val container: ComposeContainer

    init {
        // Validate that when external backends are requested, the config is present
        require(!(vdrDriverDefault == "prism-node" && prismNode == null)) {
            "prism-node driver selected but prism_node service configuration is missing"
        }
        require(!(vdrDriverDefault == "neoprism" && neoprism == null)) {
            "neoprism driver selected but neoprism service configuration is missing"
        }

        val selectedDriver =
            vdrDriverDefault ?: when {
                prismNode != null -> "prism-node"
                neoprism != null -> "neoprism"
                else -> "memory"
            }

        fun flag(envName: String, fallback: () -> Boolean): String =
            System.getenv(envName) ?: fallback().toString()

        val env = mutableMapOf(
            "AGENT_VERSION" to version,
            "API_KEY_ENABLED" to authEnabled.toString(),
            "AGENT_DIDCOMM_PORT" to didcommPort.toString(),
            "DIDCOMM_SERVICE_URL" to (didcommServiceUrl ?: "http://host.docker.internal:$didcommPort"),
            "AGENT_HTTP_PORT" to httpPort.toString(),
            "REST_SERVICE_URL" to (restServiceUrl ?: "http://host.docker.internal:$httpPort"),
            "NODE_BACKEND" to when (selectedDriver) {
                "neoprism" -> "neoprism"
                "prism-node" -> "prism-node"
                else -> ""
            },
            "PRISM_NODE_PORT" to (prismNode?.httpPort?.toString() ?: "50053"),
            "PRISM_NODE_VERSION" to (prismNode?.version ?: ""),
            "NEOPRISM_BASE_URL" to (neoprism?.let { "http://host.docker.internal:${it.httpPort}" } ?: "http://host.docker.internal:8080"),
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
            // VDR driver selection (mutually exclusive)
            "VDR_MEMORY_DRIVER_ENABLED" to flag("VDR_MEMORY_DRIVER_ENABLED") {
                selectedDriver == "memory" || selectedDriver == "prism-node"
            },
            "VDR_DATABASE_DRIVER_ENABLED" to flag("VDR_DATABASE_DRIVER_ENABLED") {
                selectedDriver == "database" || selectedDriver == "prism-node"
            },
            "VDR_PRISM_DRIVER_ENABLED" to (
                System.getenv("VDR_PRISM_DRIVER_ENABLED") ?: "false"
            ),
            "VDR_PRISM_NODE_DRIVER_ENABLED" to (
                System.getenv("VDR_PRISM_NODE_DRIVER_ENABLED") ?: (selectedDriver == "prism-node").toString()
            ),
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
