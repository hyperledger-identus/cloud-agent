package org.hyperledger.identus.sharedtest.containers

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.utility.DockerImageName

object PostgresTestContainer {
  def postgresContainer(
      imageName: Option[String] = Some("postgres:13"),
      verbose: Boolean = false
  ): PostgreSQLContainer = {
    val container =
      if (sys.env.contains("GITHUB_NETWORK"))
        new PostgreSQLContainerCustom(dockerImageNameOverride = imageName.map(DockerImageName.parse))
      else
        new PostgreSQLContainer(dockerImageNameOverride = imageName.map(DockerImageName.parse))
    sys.env.get("GITHUB_NETWORK").map { network =>
      container.container.withNetworkMode(network)
    }
    if (verbose) {
      container.container
        .withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
        .withCommand("postgres", "-c", "log_statement=all", "-c", "log_destination=stderr")
    }
    container
  }
}
