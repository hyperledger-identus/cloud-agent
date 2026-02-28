package org.hyperledger.identus.sharedtest.containers

import com.dimafeng.testcontainers.SingleContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.vault.VaultContainer as JavaVaultContainer

/** See PostgreSQLContainerCustom for explanation */
class VaultContainerCustom(
    dockerImageNameOverride: DockerImageName,
    vaultToken: Option[String] = None,
    isOnGithubRunner: Boolean = false,
    useFileBackend: Boolean = false
) extends SingleContainer[JavaVaultContainer[?]] {

  private val vaultFSBackendConfig: String =
    """{
      |  "storage": {
      |    "file": { "path": "/vault/data" }
      |  }
      |}
      """.stripMargin

  private val vaultMemBackendConfig: String =
    """{
      |  "storage": {
      |    "inmem": {}
      |  }
      |}
      """.stripMargin

  private val vaultContainer: JavaVaultContainer[?] = new JavaVaultContainer(dockerImageNameOverride) {
    override def getHost: String = {
      if (isOnGithubRunner) super.getContainerId().take(12)
      else super.getHost()
    }
    override def getMappedPort(originalPort: Int): Integer = {
      if (isOnGithubRunner) 8200
      else super.getMappedPort(originalPort)
    }
  }

  if (vaultToken.isDefined) vaultContainer.withVaultToken(vaultToken.get)

  override val container: JavaVaultContainer[?] = {
    val con = vaultContainer
    if (useFileBackend) con.addEnv("VAULT_LOCAL_CONFIG", vaultFSBackendConfig)
    else con.addEnv("VAULT_LOCAL_CONFIG", vaultMemBackendConfig)
    con
  }
}
