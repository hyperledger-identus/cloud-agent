package org.hyperledger.identus.iam.authentication.admin

import org.hyperledger.identus.iam.authentication.{AuthenticationError, Credentials}
import org.hyperledger.identus.shared.models.StatusCode

case class AdminApiKeyAuthenticationError(message: String)
    extends AuthenticationError(
      StatusCode.Unauthorized,
      message
    )

object AdminApiKeyAuthenticationError {
  val invalidAdminApiKey = AdminApiKeyAuthenticationError("Invalid Admin API key in header `x-admin-api-key`")
  val emptyAdminApiKey = AdminApiKeyAuthenticationError("Empty `x-admin-api-key` header provided")
}

case class AdminApiKeyCredentials(apiKey: Option[String]) extends Credentials
