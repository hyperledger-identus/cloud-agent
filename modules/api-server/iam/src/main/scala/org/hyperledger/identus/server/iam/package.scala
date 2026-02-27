package org.hyperledger.identus.server

/** Re-exports from iam.authentication and iam.authorization for the api-server IAM module.
  *
  * These type aliases establish the public surface for identity and access management. In a future phase, the actual
  * implementations will be moved here.
  *
  * Subsystem groupings:
  *   - Authentication: Authenticator, DefaultAuthenticator, ApiKeyAuthenticator, KeycloakAuthenticator
  *   - Authorization: PermissionManagementService, EntityPermissionManagementService
  *   - Configuration: AuthenticationConfig, AdminConfig, ApiKeyConfig, KeycloakConfig
  */
package object iam {
  // Authentication
  type Authenticator[E <: org.hyperledger.identus.agent.walletapi.model.BaseEntity] =
    org.hyperledger.identus.iam.authentication.Authenticator[E]
  type DefaultAuthenticator = org.hyperledger.identus.iam.authentication.DefaultAuthenticator
  val DefaultAuthenticator = org.hyperledger.identus.iam.authentication.DefaultAuthenticator
  type ApiKeyAuthenticator = org.hyperledger.identus.iam.authentication.apikey.ApiKeyAuthenticator
  type KeycloakAuthenticator = org.hyperledger.identus.iam.authentication.oidc.KeycloakAuthenticator

  // Authorization
  type PermissionManagementService[E <: org.hyperledger.identus.agent.walletapi.model.BaseEntity] =
    org.hyperledger.identus.iam.authorization.core.PermissionManagementService[E]
  type EntityPermissionManagementService =
    org.hyperledger.identus.iam.authorization.core.EntityPermissionManagementService

  // Configuration
  type AuthenticationConfig = org.hyperledger.identus.iam.authentication.AuthenticationConfig
  type AdminConfig = org.hyperledger.identus.iam.authentication.admin.AdminConfig
  type ApiKeyConfig = org.hyperledger.identus.iam.authentication.apikey.ApiKeyConfig
  type KeycloakConfig = org.hyperledger.identus.iam.authentication.oidc.KeycloakConfig
}
