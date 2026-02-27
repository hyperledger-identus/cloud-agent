package org.hyperledger.identus.test.container

import org.hyperledger.identus.agent.walletapi.vault.{VaultKVClient, VaultKVClientImpl}
import org.hyperledger.identus.sharedtest.containers.VaultContainerCustom
import zio.*
import zio.http.Client

trait VaultTestContainerSupport {

  private val TEST_TOKEN = "root"

  protected def vaultContainerLayer(useFileBackend: Boolean): TaskLayer[VaultContainerCustom] =
    VaultLayer.vaultLayer(vaultToken = TEST_TOKEN, useFileBackend = useFileBackend)

  protected def vaultKvClientLayer(useFileBackend: Boolean = false): TaskLayer[VaultKVClient] =
    vaultContainerLayer(useFileBackend) ++ Client.default >>> ZLayer.fromFunction { (container: VaultContainerCustom) =>
      val address = container.container.getHttpHostAddress()
      ZLayer.fromZIO(VaultKVClientImpl.fromToken(address, TEST_TOKEN))
    }.flatten

}
