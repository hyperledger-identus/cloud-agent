package org.hyperledger.identus.credentialstatus.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.codec.DidCommIDCodec.given
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.credentialstatus.controller.http.StatusListCredential
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.pollux.core.model.DidCommID
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

import java.util.UUID
object CredentialStatusEndpoints {

  val getCredentialStatusListEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    ErrorResponse,
    StatusListCredential,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-status" / path[UUID]("id").description(
          "Globally unique identifier of the credential status list"
        )
      )
      .out(jsonBody[StatusListCredential].description("Status List credential with embedded proof found by ID"))
      .errorOut(basicFailuresAndNotFound)
      .name("getCredentialStatusListEndpoint")
      .summary("Fetch credential status list by its ID")
      .description(
        "Fetch credential status list by its ID"
      )
      .tag("Credential status list")

  val revokeCredentialByIdEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, DidCommID),
    ErrorResponse,
    Unit,
    Any
  ] =
    endpoint.patch
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-status" / "revoke-credential" / path[DidCommID]("id").description("Revoke a credential by its ID")
      )
      .out(statusCode(sttp.model.StatusCode.Ok))
      .errorOut(basicFailuresWith(FailureVariant.unprocessableEntity, FailureVariant.notFound))
      .summary("Revoke a credential by its ID")
      .description("Marks credential to be ready for revocation, it will be revoked automatically")
      .tag("Credential status list")
}
