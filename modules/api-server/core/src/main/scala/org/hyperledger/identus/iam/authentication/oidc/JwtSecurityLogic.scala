package org.hyperledger.identus.iam.authentication.oidc

import sttp.tapir.ztapir.*
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.EndpointInput.AuthType.Http

object JwtSecurityLogic {
  val jwtAuthHeader: Auth[JwtCredentials, Http] = auth
    .bearer[Option[String]]()
    .mapTo[JwtCredentials]
    .securitySchemeName("jwtAuth")
}
