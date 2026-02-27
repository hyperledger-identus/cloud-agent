package org.hyperledger.identus.pollux.credentialschema

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.codec.OrderCodec.*
import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.pollux.credentialschema.http.*
import sttp.apispec.Tag
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

import java.util.UUID

object VerificationPolicyEndpoints {

  private val tagName = "Verification"
  private val tagDescription = s"""
    |The __${tagName}__ endpoints enable the management and lookup of verification policies,which are applied to W3C Verifiable Credentials in JWT format.
    |
    |Users can retrieve and paginate existing policies or create new ones.
    |These policies determine the verification criteria, allowing users to specify constraints such as `schemaId` and `trustedIssuers` in the current implementation.
    |
    |The constraints are defined using the `schemaId` and a sequence of `trustedIssuers`.
    |This functionality ensures the system's integrity and adherence to specific verification requirements.
    |
    |Endpoints are secured by __apiKeyAuth__ or __jwtAuth__ authentication.""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  val createVerificationPolicyEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, VerificationPolicyInput),
    ErrorResponse,
    VerificationPolicyResponse,
    Any
  ] = endpoint.post
    .securityIn(apiKeyHeader)
    .securityIn(jwtAuthHeader)
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in("verification" / "policies")
    .in(
      jsonBody[VerificationPolicyInput].description(
        "Create verification policy object"
      )
    )
    .out(
      statusCode(StatusCode.Created).description(
        "Verification policy successfully created"
      )
    )
    .out(
      jsonBody[VerificationPolicyResponse].description(
        "Created verification policy entity"
      )
    )
    .errorOut(basicFailuresAndForbidden)
    .name("createVerificationPolicy")
    .summary("Create the new verification policy")
    .description("Create the new verification policy")
    .tag(tagName)

  val updateVerificationPolicyEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, Int, VerificationPolicyInput),
    ErrorResponse,
    VerificationPolicyResponse,
    Any
  ] =
    endpoint.put
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("verification" / "policies" / path[UUID]("id"))
      .in(
        query[Int](name = "nonce").description(
          "Nonce of the previous VerificationPolicy"
        )
      )
      .in(
        jsonBody[VerificationPolicyInput].description(
          "Update verification policy object"
        )
      )
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[VerificationPolicyResponse])
      .errorOut(basicFailureAndNotFoundAndForbidden)
      .name("updateVerificationPolicy")
      .summary("Update the verification policy object by id")
      .description(
        "Update the verification policy entry"
      )
      .tag(tagName)

  val getVerificationPolicyByIdEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID),
    ErrorResponse,
    VerificationPolicyResponse,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "verification" / "policies" / path[UUID]("id")
          .description("Get the verification policy by id")
      )
      .out(jsonBody[VerificationPolicyResponse])
      .errorOut(basicFailureAndNotFoundAndForbidden)
      .name("getVerificationPolicyById")
      .summary("Fetch the verification policy by id")
      .description(
        "Get the verification policy by id"
      )
      .tag(tagName)

  val deleteVerificationPolicyByIdEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID),
    ErrorResponse,
    Unit,
    Any
  ] =
    endpoint.delete
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "verification" / "policies" / path[UUID]("id")
          .description("Delete the verification policy by id")
      )
      .out(
        statusCode(StatusCode.Ok).description(
          "Verification policy deleted successfully"
        )
      )
      .errorOut(basicFailureAndNotFoundAndForbidden)
      .name("deleteVerificationPolicyById")
      .summary("Deleted the verification policy by id")
      .description(
        "Delete the verification policy by id"
      )
      .tag(tagName)

  val lookupVerificationPoliciesByQueryEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, VerificationPolicyResponse.Filter, PaginationInput, Option[Order]),
    ErrorResponse,
    VerificationPolicyResponsePage,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "verification" / "policies"
          .description("Lookup verification policy by query")
      )
      .in(
        query[Option[String]]("name")
          .description(VerificationPolicyResponse.annotations.name.description)
          .mapTo[VerificationPolicyResponse.Filter]
      )
      .in(
        query[Option[Int]]("offset")
          .and(query[Option[Int]]("limit"))
          .mapTo[PaginationInput]
      )
      .in(query[Option[Order]]("order"))
      .out(jsonBody[VerificationPolicyResponsePage])
      .errorOut(basicFailuresAndForbidden)
      .name("lookupVerificationPoliciesByQuery")
      .summary("Lookup verification policies by query")
      .description(
        "Lookup verification policies by `name`, and control the pagination by `offset` and `limit` parameters"
      )
      .tag(tagName)
}
