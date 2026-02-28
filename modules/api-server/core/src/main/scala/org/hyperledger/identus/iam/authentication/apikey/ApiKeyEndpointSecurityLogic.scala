package org.hyperledger.identus.iam.authentication.apikey

import sttp.tapir.ztapir.*
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.EndpointInput.AuthType.ApiKey

object ApiKeyEndpointSecurityLogic {
  val apiKeyHeader: Auth[ApiKeyCredentials, ApiKey] = auth
    .apiKey(
      header[Option[String]]("apikey")
        .mapTo[ApiKeyCredentials]
        .description("API key")
    )
    .securitySchemeName("apiKeyAuth")
}
