package org.hyperledger.identus.iam.authentication.oidc

import org.hyperledger.identus.agent.server.config.AppConfig
import zio.*

import java.net.URL

final case class KeycloakConfig(
    enabled: Boolean,
    keycloakUrl: URL,
    realmName: String,
    clientId: String,
    clientSecret: String,
    autoUpgradeToRPT: Boolean,
    rolesClaimPath: String,
) {
  val rolesClaimPathSegments: Seq[String] = rolesClaimPath.split('.').toSeq
}

object KeycloakConfig {
  val layer: URLayer[AppConfig, KeycloakConfig] =
    ZLayer.fromFunction((conf: AppConfig) => conf.agent.authentication.keycloak)
}
