package org.hyperledger.identus.iam.authentication

import org.hyperledger.identus.iam.authentication.admin.AdminConfig
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyConfig
import org.hyperledger.identus.iam.authentication.oidc.KeycloakConfig

final case class AuthenticationConfig(
    admin: AdminConfig,
    apiKey: ApiKeyConfig,
    keycloak: KeycloakConfig
) {

  /** Return true if at least 1 authentication method is enabled (excluding admin auth method) */
  def isEnabledAny: Boolean = apiKey.enabled || keycloak.enabled

}
