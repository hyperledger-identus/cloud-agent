package org.hyperledger.identus.iam.authentication.oidc

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
