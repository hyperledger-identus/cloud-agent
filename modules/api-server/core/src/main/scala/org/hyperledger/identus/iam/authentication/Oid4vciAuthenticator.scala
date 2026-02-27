package org.hyperledger.identus.iam.authentication

import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, EntityRole}
import org.hyperledger.identus.iam.authentication.oidc.{
  AccessToken,
  JwtAuthenticationError,
  JwtCredentials,
  Oauth2TokenIntrospector,
  RemoteOauth2TokenIntrospector
}
import org.hyperledger.identus.oid4vci.service.OIDCCredentialIssuerService
import org.hyperledger.identus.pollux.core.service.OID4VCIIssuerMetadataService
import zio.*
import zio.http.Client

import java.util.UUID

final case class ExternalEntity(id: UUID) extends BaseEntity {
  override def role: Either[String, EntityRole] = Right(EntityRole.ExternalParty)
}

case class Oid4vciAuthenticator(tokenIntrospector: Oauth2TokenIntrospector) extends Authenticator[ExternalEntity] {

  override def isEnabled: Boolean = true

  def authenticate(credentials: Credentials): IO[AuthenticationError, ExternalEntity] = {
    credentials match {
      case JwtCredentials(Some(token)) if token.nonEmpty => authenticate(token)
      case JwtCredentials(Some(_))                       => ZIO.fail(JwtAuthenticationError.emptyToken)
      case JwtCredentials(None) => ZIO.fail(AuthenticationError.InvalidCredentials("Bearer token is not provided"))
      case other                => ZIO.fail(AuthenticationError.InvalidCredentials("Bearer token is not provided"))
    }
  }

  private def authenticate(token: String): IO[AuthenticationError, ExternalEntity] = {
    for {
      accessToken <- ZIO
        .fromEither(AccessToken.fromString(token))
        .mapError(AuthenticationError.InvalidCredentials.apply)
      introspection <- tokenIntrospector
        .introspectToken(accessToken)
        .mapError(e => AuthenticationError.UnexpectedError(e.getMessage))
      _ <- ZIO
        .fail(AuthenticationError.InvalidCredentials("The accessToken is invalid."))
        .unless(introspection.active)
      entityId <- ZIO
        .fromOption(introspection.sub)
        .mapError(_ => AuthenticationError.UnexpectedError("Subject ID is not found in the accessToken."))
        .flatMap { id =>
          ZIO
            .attempt(UUID.fromString(id))
            .mapError(e => AuthenticationError.UnexpectedError(s"Subject ID in accessToken is not a UUID. $e"))
        }
    } yield ExternalEntity(entityId)
  }
}

class Oid4vciAuthenticatorFactory(
    httpClient: Client,
    issuerService: OIDCCredentialIssuerService,
    metadataService: OID4VCIIssuerMetadataService
) {
  def make(issuerState: String): IO[AuthenticationError, Oid4vciAuthenticator] =
    issuerService
      .getIssuanceSessionByIssuerState(issuerState)
      .mapError(e =>
        AuthenticationError.UnexpectedError(s"Unable to get issuanceSession from issuerState: $issuerState")
      )
      .flatMap(session => make(session.issuerId))

  def make(issuerId: UUID): IO[AuthenticationError, Oid4vciAuthenticator] =
    for {
      issuer <- metadataService
        .getCredentialIssuer(issuerId)
        .mapError(e => AuthenticationError.UnexpectedError(s"Unable to get issuer from issuerId: $issuerId"))
      tokenIntrospector <- RemoteOauth2TokenIntrospector
        .fromAuthorizationServer(
          httpClient,
          issuer.authorizationServer,
          issuer.authorizationServerClientId,
          issuer.authorizationServerClientSecret
        )
        .mapError(e => AuthenticationError.UnexpectedError(s"Unable to create token introspector: $e"))
    } yield Oid4vciAuthenticator(tokenIntrospector)
}

object Oid4vciAuthenticatorFactory {
  def layer: URLayer[Client & OIDCCredentialIssuerService & OID4VCIIssuerMetadataService, Oid4vciAuthenticatorFactory] =
    ZLayer.fromFunction(Oid4vciAuthenticatorFactory(_, _, _))
}
