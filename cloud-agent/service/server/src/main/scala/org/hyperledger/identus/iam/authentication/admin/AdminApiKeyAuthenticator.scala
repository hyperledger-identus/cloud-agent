package org.hyperledger.identus.iam.authentication.admin

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.iam.authentication.{
  AuthenticationError,
  AuthenticatorWithAuthZ,
  Credentials,
  EntityAuthorizer
}
import zio.*

trait AdminApiKeyAuthenticator extends AuthenticatorWithAuthZ[Entity], EntityAuthorizer {

  def authenticate(credentials: Credentials): IO[AuthenticationError, Entity] = {
    credentials match {
      case AdminApiKeyCredentials(Some(apiKey)) => authenticate(apiKey)
      case AdminApiKeyCredentials(None)         =>
        ZIO.logDebug(s"AdminApiKey API authentication is enabled, but `x-admin-api-key` token is empty") *>
          ZIO.fail(AdminApiKeyAuthenticationError.emptyAdminApiKey)
    }
  }
  def authenticate(adminApiKey: String): IO[AuthenticationError, Entity]
}
