package org.hyperledger.identus.pollux.prex

import org.hyperledger.identus.api.http.{EndpointOutputs, ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.pollux.prex.http.{CreatePresentationDefinition, PresentationDefinitionPage}
import org.hyperledger.identus.pollux.prex.http.PresentationExchangeTapirSchemas.given
import sttp.apispec.Tag
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

import java.util.UUID

object PresentationExchangeEndpoints {

  private val tagName = "Presentation Exchange"
  private val tagDescription =
    s"""
       |The __${tagName}__ endpoints offers a way to manage resources related to [presentation exchange protocol](https://identity.foundation/presentation-exchange/spec/v2.1.1/).
       |
       |The verifier can create the resources such as `presentation-definition` that can be publicly referenced
       |in various protocols such as [OpenID for Verificable Presentation](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html).
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  private val paginationInput: EndpointInput[PaginationInput] = EndpointInput.derived[PaginationInput]

  private val baseEndpoint = endpoint
    .tag(tagName)
    .in("presentation-exchange")
    .in(extractFromRequest[RequestContext](RequestContext.apply))

  private val basePrivateEndpoint = baseEndpoint
    .securityIn(apiKeyHeader)
    .securityIn(jwtAuthHeader)

  val getPresentationDefinition: Endpoint[
    Unit,
    (RequestContext, UUID),
    ErrorResponse,
    PresentationDefinition,
    Any
  ] =
    baseEndpoint.get
      .in("presentation-definitions" / path[UUID]("id"))
      .out(statusCode(StatusCode.Ok).description("Presentation Definition retrieved successfully"))
      .out(jsonBody[PresentationDefinition])
      .errorOut(EndpointOutputs.basicFailuresAndNotFound)
      .name("getPresentationDefinition")
      .summary("Get a presentation-definition")

  val listPresentationDefinition: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, PaginationInput),
    ErrorResponse,
    PresentationDefinitionPage,
    Any,
  ] =
    basePrivateEndpoint.get
      .in("presentation-definitions")
      .in(paginationInput)
      .out(statusCode(StatusCode.Ok).description("Presentation Definitions retrieved successfully"))
      .out(jsonBody[PresentationDefinitionPage])
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("listPresentationDefinition")
      .summary("List all presentation-definitions")
      .description(
        """List all `presentation-definitions` in the wallet.
          |Return a paginated items ordered by created timestamp.""".stripMargin
      )

  val createPresentationDefinition: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CreatePresentationDefinition),
    ErrorResponse,
    PresentationDefinition,
    Any
  ] =
    basePrivateEndpoint.post
      .in("presentation-definitions")
      .in(jsonBody[CreatePresentationDefinition])
      .out(statusCode(StatusCode.Created).description("Presentation Definition created successfully"))
      .out(jsonBody[PresentationDefinition])
      .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
      .name("createPresentationDefinition")
      .summary("Create a new presentation-definition")
      .description(
        """Create a `presentation-definition` object according to the [presentation exchange protocol](https://identity.foundation/presentation-exchange/spec/v2.1.1/).
          |The `POST` endpoint is restricted to the owner of the wallet. The `presentation-definition` object, however can be referenced by publicly by `id` returned in the response.""".stripMargin
      )

}
