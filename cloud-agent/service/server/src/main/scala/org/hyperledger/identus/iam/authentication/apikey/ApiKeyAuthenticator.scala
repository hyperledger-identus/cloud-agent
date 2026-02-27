package org.hyperledger.identus.iam.authentication.apikey

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.iam.authentication.{
  AuthenticationError,
  AuthenticatorWithAuthZ,
  Credentials,
  EntityAuthorizer
}
import org.hyperledger.identus.iam.authentication.AuthenticationError.*
import zio.{IO, UIO, ZIO}

import java.util.UUID

trait ApiKeyAuthenticator extends AuthenticatorWithAuthZ[Entity], EntityAuthorizer {

  def authenticate(credentials: Credentials): IO[AuthenticationError, Entity] = {
    if (isEnabled) {
      credentials match {
        case ApiKeyCredentials(apiKey) =>
          apiKey match {
            case Some(value) if value.nonEmpty => authenticate(value)
            case Some(value)                   =>
              ZIO.logDebug(s"ApiKey API authentication is enabled, but `apikey` token is empty") *>
                ZIO.fail(ApiKeyAuthenticationError.emptyApiKey)
            case None =>
              ZIO.logDebug(s"ApiKey API authentication is enabled, but `apikey` token is not provided") *>
                ZIO.fail(InvalidCredentials("ApiKey key is not provided"))
          }
        case other =>
          ZIO.fail(InvalidCredentials("ApiKey key is not provided"))
      }
    } else ZIO.fail(AuthenticationMethodNotEnabled("ApiKey API authentication is not enabled"))
  }

  def isEnabled: Boolean

  def authenticate(apiKey: String): IO[InvalidCredentials, Entity]

  def add(entityId: UUID, apiKey: String): UIO[Unit]

  def delete(entityId: UUID, apiKey: String): UIO[Unit]
}

object ApiKeyAuthenticator {
  def authenticate(credentials: Credentials): ZIO[ApiKeyAuthenticator, AuthenticationError, Entity] =
    ZIO.serviceWithZIO[ApiKeyAuthenticator](_.authenticate(credentials))

  def add(entityId: UUID, apiKey: String): ZIO[ApiKeyAuthenticator, AuthenticationError, Unit] =
    ZIO.serviceWithZIO[ApiKeyAuthenticator](_.add(entityId, apiKey))
}
