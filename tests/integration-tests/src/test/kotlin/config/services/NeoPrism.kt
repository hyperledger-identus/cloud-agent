package config.services

import com.sksamuel.hoplite.ConfigAlias
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

data class NeoPrism(
    @ConfigAlias("http_port") val httpPort: Int,
    val version: String,
) : ServiceBase() {
    override val logServices: List<String> = listOf("neoprism")
    private val neoprismComposeFile = "src/test/resources/containers/neoprism.yml"
    override val container: ComposeContainer = ComposeContainer(File(neoprismComposeFile)).withEnv(
        mapOf(
            "NEOPRISM_VERSION" to version,
            "NEOPRISM_PORT" to httpPort.toString(),
        ),
    ).waitingFor(
        "neoprism",
        Wait.forHealthcheck(),
    )
}
