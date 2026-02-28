package org.hyperledger.identus.iam.authentication.admin

import sttp.tapir.ztapir.*
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.EndpointInput.AuthType.ApiKey

object AdminApiKeySecurityLogic {

  val adminApiKeyHeader: Auth[AdminApiKeyCredentials, ApiKey] = auth
    .apiKey(
      header[Option[String]]("x-admin-api-key")
        .mapTo[AdminApiKeyCredentials]
        .description("Admin API Key")
    )
    .securitySchemeName("adminApiKeyAuth")

}
