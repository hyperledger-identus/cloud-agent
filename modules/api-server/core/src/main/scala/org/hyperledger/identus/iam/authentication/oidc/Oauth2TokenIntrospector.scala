package org.hyperledger.identus.iam.authentication.oidc

import zio.*
import zio.http.*
import zio.json.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

final case class AuthorizationServerMetadata(introspection_endpoint: String)

object AuthorizationServerMetadata {
  given JsonEncoder[AuthorizationServerMetadata] = JsonEncoder.derived
  given JsonDecoder[AuthorizationServerMetadata] = JsonDecoder.derived
}

final case class TokenIntrospection(active: Boolean, sub: Option[String])

object TokenIntrospection {
  given JsonEncoder[TokenIntrospection] = JsonEncoder.derived
  given JsonDecoder[TokenIntrospection] = JsonDecoder.derived
}

trait Oauth2TokenIntrospector {
  def introspectToken(token: AccessToken): IO[Throwable, TokenIntrospection]
}

// TODO: support offline introspection
class RemoteOauth2TokenIntrospector(
    introspectionUrl: String,
    httpClient: Client,
    clientId: String,
    clientSecret: String
) extends Oauth2TokenIntrospector {

  private val baseFormHeaders = Headers(Header.ContentType(MediaType.application.`x-www-form-urlencoded`))

  // https://www.keycloak.org/docs/22.0.4/securing_apps/#_token_introspection_endpoint
  override def introspectToken(token: AccessToken): Task[TokenIntrospection] = {
    (for {
      url <- ZIO.fromEither(URL.decode(introspectionUrl)).orDie
      response <- httpClient
        .request(
          Request(
            url = url,
            method = Method.POST,
            headers = baseFormHeaders ++ Headers(
              Header.Authorization.Basic(
                username = URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                password = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
              )
            ),
            body = Body.fromURLEncodedForm(
              Form(
                FormField.simpleField("token", token.toString)
              )
            )
          )
        )
        .logError("Fail to introspect token on keycloak.")
      body <- response.body.asString
        .logError("Fail parse keycloak introspection response.")
      result <-
        if (response.status.code == 200) {
          ZIO
            .fromEither(body.fromJson[TokenIntrospection])
            .logError("Fail to decode keycloak token introspection response")
            .mapError(RuntimeException(_))
        } else {
          ZIO.logError(s"Keycloak token introspection was unsucessful. Status: ${response.status}. Response: $body") *>
            ZIO.fail(RuntimeException("Token introspection did not return a successful result."))
        }
    } yield result).provide(Scope.default)
  }

}

object RemoteOauth2TokenIntrospector {
  def fromAuthorizationServer(
      httpClient: Client,
      authorizationServer: java.net.URL,
      clientId: String,
      clientSecret: String
  ): Task[RemoteOauth2TokenIntrospector] = {
    for {
      url <- ZIO.fromEither(URL.decode(authorizationServer.toString())).orDie
      metadataUrl = url / ".well-known" / "openid-configuration"
      response <- httpClient.request(Request(url = metadataUrl, method = Method.GET))
      body <- response.body.asString
      metadata <- ZIO
        .fromEither(body.fromJson[AuthorizationServerMetadata])
        .mapError(e => RuntimeException(s"Unable to parse authorization server metadata: $e"))
    } yield RemoteOauth2TokenIntrospector(metadata.introspection_endpoint, httpClient, clientId, clientSecret)
  }.provide(Scope.default)
}
