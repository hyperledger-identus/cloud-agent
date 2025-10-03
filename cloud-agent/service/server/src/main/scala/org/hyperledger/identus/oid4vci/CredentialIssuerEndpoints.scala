package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.api.http.{EndpointOutputs, ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.EndpointOutputs.FailureVariant
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.oid4vci.http.*
import org.hyperledger.identus.oid4vci.http.ExtendedErrorResponse.given
import sttp.apispec.Tag
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

import java.util.UUID

object CredentialIssuerEndpoints {

  private val tagName = "OpenID for Verifiable Credential Issuance"
  private val tagDescription =
    s"""
       |The __${tagName}__ is a service that issues credentials to users by implementing the [OIDC for Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) specification.
       |It exposes the following endpoints:
       |- Credential Endpoint
       |- Credential Issuer Metadata Endpoint
       |- Credential Offer Endpoint
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  private val issuerIdPathSegment = path[UUID]("issuerId")
    .description("An issuer identifier in the oid4vci protocol")
    .example(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"))

  private val credentialConfigIdSegment = path[String]("credentialConfigId")
    .description("An identifier for the credential configuration")
    .example("UniversityDegree")

  private val baseEndpoint = endpoint
    .tag(tagName)
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in("oid4vci")

  private val baseIssuerEndpoint = baseEndpoint.in("issuers")

  private val baseIssuerPrivateEndpoint = baseIssuerEndpoint
    .securityIn(apiKeyHeader)
    .securityIn(jwtAuthHeader)

  val credentialEndpointErrorOutput = oneOf[ExtendedErrorResponse](
    oneOfVariantValueMatcher(StatusCode.BadRequest, jsonBody[ExtendedErrorResponse]) {
      case CredentialErrorResponse(code, _, _, _) if code.toHttpStatusCode == StatusCode.BadRequest => true
    },
    oneOfVariantValueMatcher(StatusCode.Unauthorized, jsonBody[ExtendedErrorResponse]) {
      case CredentialErrorResponse(code, _, _, _) if code.toHttpStatusCode == StatusCode.Unauthorized => true
    },
    oneOfVariantValueMatcher(StatusCode.Forbidden, jsonBody[ExtendedErrorResponse]) {
      case CredentialErrorResponse(code, _, _, _) if code.toHttpStatusCode == StatusCode.Forbidden => true
    },
    oneOfVariantValueMatcher(StatusCode.InternalServerError, jsonBody[ExtendedErrorResponse]) {
      case ErrorResponse(status, _, _, _, _) if status == StatusCode.InternalServerError.code => true
    }
  )

  val credentialEndpoint: Endpoint[
    JwtCredentials,
    (RequestContext, UUID, CredentialRequest),
    ExtendedErrorResponse,
    CredentialResponse,
    Any
  ] = baseIssuerEndpoint.post
    .in(issuerIdPathSegment / "credentials")
    .in(jsonBody[CredentialRequest])
    .securityIn(jwtAuthHeader)
    .out(
      statusCode(StatusCode.Ok).description("Credential issued successfully"),
    )
    .out(jsonBody[CredentialResponse])
    .errorOut(credentialEndpointErrorOutput)
    .name("oid4vciIssueCredential")
    .summary("Credential Endpoint")
    .description(
      """OID for VCI [Credential Endpoint](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-credential-endpoint)""".stripMargin
    )

  val createCredentialOfferEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, CredentialOfferRequest),
    ErrorResponse,
    CredentialOfferResponse,
    Any
  ] = baseIssuerPrivateEndpoint.post
    .in(issuerIdPathSegment / "credential-offers")
    .in(jsonBody[CredentialOfferRequest])
    .out(
      statusCode(StatusCode.Created).description("CredentialOffer created successfully"),
    )
    .out(jsonBody[CredentialOfferResponse])
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("oid4vciCreateCredentialOffer")
    .summary("Create a new credential offer")
    .description(
      """Create a new credential offer and return a compliant `CredentialOffer` for the holder's
        |[Credential Offer Endpoint](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-credential-offer-endpoint).""".stripMargin
    )

  val nonceEndpoint: Endpoint[
    JwtCredentials,
    (RequestContext, NonceRequest),
    ErrorResponse,
    NonceResponse,
    Any
  ] = baseEndpoint.post
    .in("nonces")
    .in(jsonBody[NonceRequest])
    .securityIn(jwtAuthHeader)
    .out(
      statusCode(StatusCode.Ok).description("Nonce issued successfully"),
    )
    .out(jsonBody[NonceResponse])
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("getNonce")
    .summary("Nonce Endpoint")
    .description(
      """The endpoint that returns a `nonce` value for the [Token Endpoint](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-nonce-endpoint)""".stripMargin
    )

  val createCredentialIssuerEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CreateCredentialIssuerRequest),
    ErrorResponse,
    CredentialIssuer,
    Any
  ] = baseIssuerPrivateEndpoint.post
    .in(jsonBody[CreateCredentialIssuerRequest])
    .out(
      statusCode(StatusCode.Created).description("Credential issuer created successfully")
    )
    .out(jsonBody[CredentialIssuer])
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("createCredentialIssuer")
    .summary("Create a new  credential issuer")

  val getCredentialIssuersEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    RequestContext,
    ErrorResponse,
    CredentialIssuerPage,
    Any
  ] = baseIssuerPrivateEndpoint.get
    .errorOut(EndpointOutputs.basicFailuresAndForbidden)
    .out(statusCode(StatusCode.Ok).description("List the credential issuers"))
    .out(jsonBody[CredentialIssuerPage])
    .name("getCredentialIssuers")
    .summary("List all credential issuers")

  val updateCredentialIssuerEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, PatchCredentialIssuerRequest),
    ErrorResponse,
    CredentialIssuer,
    Any
  ] = baseIssuerPrivateEndpoint.patch
    .in(issuerIdPathSegment)
    .in(jsonBody[PatchCredentialIssuerRequest])
    .out(
      statusCode(StatusCode.Ok).description("Credential issuer updated successfully")
    )
    .out(jsonBody[CredentialIssuer])
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("updateCredentialIssuer")
    .summary("Update the credential issuer")

  val deleteCredentialIssuerEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID),
    ErrorResponse,
    Unit,
    Any
  ] = baseIssuerPrivateEndpoint.delete
    .in(issuerIdPathSegment)
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .out(statusCode(StatusCode.Ok).description("Credential issuer deleted successfully"))
    .name("deleteCredentialIssuer")
    .summary("Delete the credential issuer")

  val createCredentialConfigurationEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, CreateCredentialConfigurationRequest),
    ErrorResponse,
    CredentialConfiguration,
    Any
  ] = baseIssuerPrivateEndpoint.post
    .in(issuerIdPathSegment / "credential-configurations")
    .in(jsonBody[CreateCredentialConfigurationRequest])
    .out(
      statusCode(StatusCode.Created).description("Credential configuration created successfully")
    )
    .out(jsonBody[CredentialConfiguration])
    .errorOut(EndpointOutputs.basicFailuresAndForbiddenWith(FailureVariant.notFound, FailureVariant.conflict))
    .name("createCredentialConfiguration")
    .summary("Create a new  credential configuration")
    .description(
      """Create a new credential configuration for the issuer.
        |It represents the configuration of the credential that can be issued by the issuer.
        |This credential configuration object will be displayed in the credential issuer metadata.""".stripMargin
    )

  val getCredentialConfigurationEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, String),
    ErrorResponse,
    CredentialConfiguration,
    Any
  ] = baseIssuerPrivateEndpoint.get
    .in(issuerIdPathSegment / "credential-configurations" / credentialConfigIdSegment)
    .out(
      statusCode(StatusCode.Ok).description("Get credential configuration successfully")
    )
    .out(jsonBody[CredentialConfiguration])
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("getCredentialConfiguration")
    .summary("Get the credential configuration")

  val deleteCredentialConfigurationEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, String),
    ErrorResponse,
    Unit,
    Any
  ] = baseIssuerPrivateEndpoint.delete
    .in(issuerIdPathSegment / "credential-configurations" / credentialConfigIdSegment)
    .out(
      statusCode(StatusCode.Ok).description("Credential configuration deleted successfully")
    )
    .errorOut(EndpointOutputs.basicFailureAndNotFoundAndForbidden)
    .name("deleteCredentialConfiguration")
    .summary("Delete the credential configuration")

  val issuerMetadataEndpoint: Endpoint[
    Unit,
    (RequestContext, UUID),
    ErrorResponse,
    IssuerMetadata,
    Any
  ] = baseIssuerEndpoint.get
    .in(issuerIdPathSegment / ".well-known" / "openid-credential-issuer")
    .out(
      statusCode(StatusCode.Ok).description("Issuer Metadata successfully retrieved")
    )
    .out(jsonBody[IssuerMetadata])
    .errorOut(EndpointOutputs.basicFailuresAndNotFound)
    .name("getIssuerMetadata")
    .summary("Get the credential issuer metadata")

}
