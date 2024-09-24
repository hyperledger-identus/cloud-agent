package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.oid4vci.http.*
import sttp.apispec.Tag
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody

object VerifiablePresentationEndpoints {

  private val tagName = "OpenID for Verifiable Presentation"
  private val tagDescription =
    s"""
       |The __${tagName}__ is a service that implements [OpenID for Verifiable Presentation](https://openid.github.io/OpenID4VP/openid-4-verifiable-presentations-wg-draft.html) specification.
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  /*  private val verifierIdPathSegment = path[UUID]("verifierId")
    .description("The verifier identifier in the oid4vp protocol")
    .example(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d47a"))*/

  private val baseEndpoint = endpoint
    .tag(tagName)
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in("oid4vp")

  private val baseVerifierEndpoint = baseEndpoint.in("verifiers")

  private val baseVerifierPrivateEndpoint = baseVerifierEndpoint
    .securityIn(jwtAuthHeader)

  val verifyEndpoint: Endpoint[
    JwtCredentials,
    (RequestContext, VerifiablePresentationRequest),
    ErrorResponse,
    VerifiablePresentationResponse,
    Any
  ] = baseVerifierPrivateEndpoint.post
    .in("verify")
    .in(jsonBody[VerifiablePresentationRequest])
    .out(
      statusCode(StatusCode.Ok).description("Verification presentation session created successfully"),
    )
    .out(jsonBody[VerifiablePresentationResponse])
    .errorOut(jsonBody[ErrorResponse])
    .name("oid4vpVerifiablePresentationRequest")
    .summary("Verifiable Presentation endpoint")
    .description(
      """Initialize OIDC Verifiable Presentation Session""".stripMargin
    )

  val responseSubmissionEndpoint: Endpoint[
    Unit,
    (RequestContext, VerifiablePresentationSubmissionForm),
    Unit,
    Unit,
    Any
  ] =
    baseEndpoint.post
      .in("submissions")
      .in(formBody[VerifiablePresentationSubmissionForm])
      .out(
        statusCode(StatusCode.Ok).description("Presentation response submitted successfully"),
      )
      // TODO: define errorOut
      .name("oid4vpResponseSubmission")
      .summary("Submit an authorization response for oid4vp direct_post response mode")

}
