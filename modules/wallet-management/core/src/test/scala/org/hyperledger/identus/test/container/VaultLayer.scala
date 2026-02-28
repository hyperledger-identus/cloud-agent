package org.hyperledger.identus.test.container

import org.hyperledger.identus.sharedtest.containers.{VaultContainerCustom, VaultTestContainer}
import zio.*

object VaultLayer {

  def vaultLayer(vaultToken: String, useFileBackend: Boolean): TaskLayer[VaultContainerCustom] = {
    ZLayer
      .scoped {
        ZIO
          .acquireRelease(ZIO.attemptBlocking {
            VaultTestContainer.vaultContainer(vaultToken = Some(vaultToken), useFileBackend = useFileBackend)
          })(container => ZIO.attemptBlocking(container.stop()).orDie)
          // Start the container outside the aquireRelease as this might fail
          // to ensure contianer.stop() is added to the finalizer
          .tap(container => ZIO.attemptBlocking(container.start()))
      }
  }

}
