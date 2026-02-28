package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.issue.controller.http.*
import sttp.apispec.Tag
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

object IssueEndpoints {

  private val tagName = "Issue Credentials Protocol"
  private val tagDescription =
    s"""
       |The __${tagName}__ endpoints facilitate the initiation of credential issuance flows between the current Agent and peer Agents, regardless of whether they reside in Cloud Agent or edge environments.
       |This implementation adheres to the [Issue Credential Protocol 3.0](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential) specification to execute credential issuance flows.
       |The flow is initiated by the issuer who creates a [credential offer](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#offer-credential) and sends it to the holder's DIDComm messaging service endpoint.
       |Upon accepting the received offer, the holder sends a [credential request](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#request-credential) to the issuer.
       |The issuer agent will then issue the credential (JWT or AnonCreds) and send an [issue credential](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#issue-credential) message containing the verifiable credential to the holder.
       |The current implementation only supports one of the three alternative beginnings proposed in the spec, which is "the Issuer begin with an offer".
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  private val paginationInput: EndpointInput[PaginationInput] = EndpointInput.derived[PaginationInput]

  val createCredentialOffer: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CreateIssueCredentialRecordRequest),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("issue-credentials" / "credential-offers")
      .in(jsonBody[CreateIssueCredentialRecordRequest].description("The credential offer object."))
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(
        statusCode(StatusCode.Created)
          .description("The credential issuance record was created successfully, and is returned in the response body.")
      )
      .out(jsonBody[IssueCredentialRecord].description("The issue credential record."))
      .name("createCredentialOffer")
      .summary("As a credential issuer, create a new credential offer that will be sent to a holder Agent.")
      .description("""
        |Creates a new credential offer that will be delivered, through a previously established DIDComm connection, to a holder Agent.
        |The subsequent credential offer message adheres to the [Issue Credential Protocol 3.0 - Offer Credential](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#offer-credential) specification.
        |The created offer can be of two types: 'JWT' or 'AnonCreds'.
        |""".stripMargin)
      .tag(tagName)

  val createCredentialOfferInvitation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CreateIssueCredentialRecordRequest),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("issue-credentials" / "credential-offers" / "invitation")
      .in(jsonBody[CreateIssueCredentialRecordRequest].description("The credential offer object."))
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(
        statusCode(StatusCode.Created)
          .description("The credential issuance record was created successfully, and is returned in the response body.")
      )
      .out(jsonBody[IssueCredentialRecord].description("The issue credential record."))
      .name("createCredentialOfferInvitation")
      .summary(
        "As a credential issuer, create a new credential offer Invitation that will be delivered as out-of-band to a peer Agent."
      )
      .description("""
        |Creates a new credential offer invitation to be delivered as an out-of-band message. 
        |The invitation message adheres to the OOB specification as outlined [here](https://identity.foundation/didcomm-messaging/spec/#invitation),
        |with the credential offer message attached according to the [Issue Credential Protocol 3.0 - Offer Credential specification](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#offer-credential).
        |The created offer attachment can be of three types: 'JWT', 'AnonCreds', or 'SDJWT'.
        |""".stripMargin)
      .tag(tagName)

  val getCredentialRecords: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, PaginationInput, Option[String]),
    ErrorResponse,
    IssueCredentialRecordPage,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("issue-credentials" / "records")
      .in(paginationInput)
      .in(
        query[Option[String]]("thid")
          .description("The thread ID associated with a specific credential issue flow execution.")
      )
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError
        )
      )
      .out(
        jsonBody[IssueCredentialRecordPage]
          .description("The list of issue credential records available found in the Agent's database.")
      )
      .name("getCredentialRecords")
      .summary("Retrieves the list of issue credential records from the Agent's database.")
      .description("""
        |Retrieves the list of issue credential records from the Agent's database.
        |The API returns a comprehensive collection of issue credential flow records within the system, regardless of their state.
        |The returned items include essential metadata such as record ID, thread ID, state, role, issued credential, and other relevant details.
        |""".stripMargin)
      .tag(tagName)

  val getCredentialRecord: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, String),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "issue-credentials" / "records" / path[String]("recordId")
          .description("The `recordId` uniquely identifying the issue credential flow record.")
      )
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(jsonBody[IssueCredentialRecord].description("The specific issue credential flow record."))
      .name("getCredentialRecord")
      .summary(
        "Retrieves a specific issue credential flow record from the Agent's database based on its unique `recordId`."
      )
      .description("""
        |Retrieves a specific issue credential flow record from the Agent's database based on its unique `recordId`.
        |The API returns a comprehensive collection of issue credential flow records within the system, regardless of their state.
        |The returned items include essential metadata such as record ID, thread ID, state, role, issued credential, and other relevant details.
        |""".stripMargin)
      .tag(tagName)

  val acceptCredentialOffer: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, String, AcceptCredentialOfferRequest),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "issue-credentials" / "records" / path[String]("recordId")
          .description("The `recordId` uniquely identifying the issue credential flow record.")
      )
      .in("accept-offer")
      .in(
        jsonBody[AcceptCredentialOfferRequest]
          .description("The accept credential offer request object.")
      )
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(
        jsonBody[IssueCredentialRecord]
          .description(
            "The issue credential offer was successfully accepted, and the updated record is returned in the response body."
          )
      )
      .name("acceptCredentialOffer")
      .summary("As a holder, accept a new credential offer received from another issuer Agent.")
      .description("""
        |As a holder, accept a new credential offer received from an issuer Agent.
        |The subsequent credential request message sent to the issuer adheres to the [Issue Credential Protocol 3.0 - Request Credential](https://github.com/decentralized-identity/waci-didcomm/tree/main/issue_credential#request-credential) specification.
        |""".stripMargin)
      .tag(tagName)

  val acceptCredentialOfferInvitation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, AcceptCredentialOfferInvitation),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "issue-credentials" / "credential-offers" / "accept-invitation"
      )
      .in(
        jsonBody[AcceptCredentialOfferInvitation]
          .description("The accept credential offer Invitation OOB message.")
      )
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(
        jsonBody[IssueCredentialRecord]
          .description(
            "The issue credential offer Invitation was successfully accepted, and new record with RequestReceived state is returned in the response body."
          )
      )
      .name("acceptCredentialOfferInvitation")
      .summary("As a holder, accept a new credential offer invitation received from another issuer Agent.")
      .description("""
        |As a holder, accept a new credential offer invitation received from an issuer Agent.
        |The credential offer request message from issuer is decoded and processed. New record with RequestReceived state is created.
        |""".stripMargin)
      .tag(tagName)

  val issueCredential: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, String),
    ErrorResponse,
    IssueCredentialRecord,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "issue-credentials" / "records" / path[String]("recordId")
          .description("The `recordId` uniquely identifying the issue credential flow record.")
      )
      .in("issue-credential")
      .errorOut(
        oneOf(
          FailureVariant.forbidden,
          FailureVariant.badRequest,
          FailureVariant.internalServerError,
          FailureVariant.notFound
        )
      )
      .out(
        jsonBody[IssueCredentialRecord]
          .description("""
          |The issue credential request was successfully processed, and the updated record is returned in the response body.
          |The credential will be generated and sent to the holder Agent asynchronously.
          |""".stripMargin)
      )
      .name("issueCredential")
      .summary("As an issuer, issues the verifiable credential related the identified issuance flow record.")
      .description(
        """
        |As an issuer, issues the verifiable credential related the identified issuance flow record.
        |The JWT or AnonCreds credential will be generated and sent to the holder Agent asynchronously and through DIDComm.
        |Note that this endpoint should only be called when automatic issuance is disabled for this record (i.e. `automaticIssuance` attribute set to `false` at offer creation time).
        |""".stripMargin
      )
      .tag(tagName)
}
