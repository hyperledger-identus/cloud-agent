package org.hyperledger.identus.sharedtest.containers

import com.dimafeng.testcontainers.SingleContainer
import dasniko.testcontainers.keycloak.ExtendableKeycloakContainer
import org.hyperledger.identus.sharedtest.containers.KeycloakTestContainer.keycloakContainer
import org.testcontainers.utility.DockerImageName
import zio.{TaskLayer, ZIO, ZLayer}

final class KeycloakContainerCustom(
    dockerImageNameOverride: DockerImageName,
    isOnGithubRunner: Boolean = false
) extends SingleContainer[ExtendableKeycloakContainer[?]] {

  private val keycloakContainer: ExtendableKeycloakContainer[?] = new ExtendableKeycloakContainer(
    dockerImageNameOverride.toString
  ) {
    override def getHost: String = {
      if (isOnGithubRunner) super.getContainerId.take(12)
      else super.getHost
    }

    override def getMappedPort(originalPort: Int): Integer = {
      if (isOnGithubRunner) 8080
      else super.getMappedPort(originalPort)
    }
  }

  override val container: ExtendableKeycloakContainer[?] = keycloakContainer
}

object KeycloakContainerCustom {
  val layer: TaskLayer[KeycloakContainerCustom] =
    ZLayer.scoped {
      ZIO
        .acquireRelease(ZIO.attemptBlockingIO {
          keycloakContainer()
        })(container => ZIO.attemptBlockingIO(container.stop()).orDie)
        .tap(container => ZIO.attemptBlocking(container.start()))
    }
}
