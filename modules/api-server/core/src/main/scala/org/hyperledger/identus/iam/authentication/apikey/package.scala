package org.hyperledger.identus.iam.authentication

import org.hyperledger.identus.shared.models.StatusCode

package object apikey {
  case class ApiKeyCredentials(apiKey: Option[String]) extends Credentials

  case class ApiKeyAuthenticationError(message: String)
      extends AuthenticationError(
        StatusCode.Unauthorized,
        message
      )

  object ApiKeyAuthenticationError {
    val invalidApiKey = ApiKeyAuthenticationError("Invalid `apikey` header provided")
    val emptyApiKey = ApiKeyAuthenticationError("Empty `apikey` header provided")
  }
}
