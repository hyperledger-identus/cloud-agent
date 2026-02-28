package org.hyperledger.identus.verification.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

object VcVerificationEndpoints {
  val verify: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, List[http.VcVerificationRequest]),
    ErrorResponse,
    List[http.VcVerificationResponse],
    Any
  ] =
    endpoint.post
      .tag("Verifiable Credentials Verification")
      .name("verify")
      .summary("Verify a set of credentials as a Verifier")
      .description("Endpoint to verify a set of verifiable credentials as a Verifier.")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in("verification" / "credential")
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(jsonBody[List[http.VcVerificationRequest]].description("List of verifiable credentials to verify"))
      .out(statusCode(StatusCode.Ok).description("List of verifiable credentials verification outcomes"))
      .out(jsonBody[List[http.VcVerificationResponse]])
      .errorOut(basicFailuresAndForbidden)
}
