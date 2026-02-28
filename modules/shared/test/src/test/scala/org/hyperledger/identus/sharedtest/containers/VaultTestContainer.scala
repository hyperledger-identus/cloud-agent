package org.hyperledger.identus.sharedtest.containers

import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.utility.DockerImageName

object VaultTestContainer {
  def vaultContainer(
      imageName: String = "hashicorp/vault:1.15.6",
      vaultToken: Option[String] = None,
      verbose: Boolean = false,
      useFileBackend: Boolean = false
  ): VaultContainerCustom = {
    val isOnGithubRunner = sys.env.contains("GITHUB_NETWORK")
    val container =
      new VaultContainerCustom(
        dockerImageNameOverride = DockerImageName.parse(imageName),
        vaultToken = vaultToken,
        isOnGithubRunner = isOnGithubRunner,
        useFileBackend = useFileBackend
      )
    sys.env.get("GITHUB_NETWORK").map { network =>
      container.container.withNetworkMode(network)
    }
    if (verbose) {
      container.container
        .withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
    }
    container
  }
}
