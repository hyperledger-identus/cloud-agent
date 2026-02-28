package org.hyperledger.identus.sharedtest.containers

import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import java.time.Duration

object KeycloakTestContainer {
  def keycloakContainer(
      imageName: String = "quay.io/keycloak/keycloak:23.0.7",
  ): KeycloakContainerCustom = {
    val isOnGithubRunner = sys.env.contains("GITHUB_NETWORK")
    val container =
      new KeycloakContainerCustom(
        dockerImageNameOverride = DockerImageName.parse(imageName),
        isOnGithubRunner = isOnGithubRunner
      )

    // modern Keycloak images use Quarkus health endpoints
    container.container
      .withEnv("KEYCLOAK_ADMIN", "admin")
      .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("testcontainers.keycloak")))
      .waitingFor(
        Wait
          .forHttp("/health/ready")
          .forStatusCode(200)
          .withStartupTimeout(Duration.ofMinutes(2))
      )

    sys.env.get("GITHUB_NETWORK").map { network =>
      container.container.withNetworkMode(network)
    }

    container
  }
}
