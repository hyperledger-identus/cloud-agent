package org.hyperledger.identus.iam.authorization.keycloak.admin

import org.hyperledger.identus.iam.authentication.oidc.KeycloakConfig
import org.hyperledger.identus.sharedtest.containers.{KeycloakContainerCustom, KeycloakTestContainerSupport}
import zio.*

import java.net.URI

trait KeycloakConfigUtils {
  this: KeycloakTestContainerSupport =>

  protected def keycloakAdminConfig: RIO[KeycloakContainerCustom, KeycloakAdminConfig] =
    for {
      keycloakContainer <- ZIO.service[KeycloakContainerCustom]
      keycloakAdminConfig = KeycloakAdminConfig(
        serverUrl = keycloakContainer.container.getAuthServerUrl,
        realm = "master",
        username = keycloakContainer.container.getAdminUsername,
        password = keycloakContainer.container.getAdminPassword,
        clientId = "admin-cli",
        clientSecret = Option.empty,
        authToken = Option.empty,
        scope = Option.empty
      )
    } yield keycloakAdminConfig

  protected val keycloakAdminConfigLayer = ZLayer.fromZIO(keycloakAdminConfig)

  protected def keycloakConfigLayer(authUpgradeToRPT: Boolean = true) =
    ZLayer.fromZIO {
      ZIO.serviceWith[KeycloakContainerCustom] { container =>
        val host = container.container.getHost()
        val port = container.container.getHttpPort()
        val url = s"http://${host}:${port}"
        KeycloakConfig(
          enabled = true,
          keycloakUrl = URI(url).toURL(),
          realmName = realmName,
          clientId = agentClientRepresentation.getClientId(),
          clientSecret = agentClientSecret,
          autoUpgradeToRPT = authUpgradeToRPT,
          rolesClaimPath = "resource_access.prism-agent.roles"
        )
      }
    }

}
