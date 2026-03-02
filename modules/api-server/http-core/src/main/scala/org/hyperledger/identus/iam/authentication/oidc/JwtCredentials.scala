package org.hyperledger.identus.iam.authentication.oidc

import org.hyperledger.identus.iam.authentication.{AuthenticationError, Credentials}
import org.hyperledger.identus.shared.models.StatusCode

final case class JwtCredentials(token: Option[String]) extends Credentials

final case class JwtAuthenticationError(message: String)
    extends AuthenticationError(
      StatusCode.Unauthorized,
      message
    )

object JwtAuthenticationError {
  val emptyToken = JwtAuthenticationError("Empty bearer token header provided")
}
