package org.hyperledger.identus.presentproof.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.presentproof.controller.http.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

import java.util.UUID

object PresentProofEndpoints {

  private val paginationInput: EndpointInput[PaginationInput] = EndpointInput.derived[PaginationInput]
  private val thidInput: EndpointInput[Option[String]] =
    query[Option[String]]("thid")
      .description("Filter by the DID Comm message's 'thid' of presentProof")

  val requestPresentation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, RequestPresentationInput),
    ErrorResponse,
    PresentationStatus,
    Any
  ] =
    endpoint.post
      .tag("Present Proof")
      .name("requestPresentation")
      .summary("As a Verifier, create a new proof presentation request and send it to the Prover.")
      .description("Holder presents proof derived from the verifiable credential to verifier.")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in("present-proof" / "presentations")
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(jsonBody[RequestPresentationInput].description("The present proof creation request."))
      .out(
        statusCode(StatusCode.Created).description(
          "The proof presentation request was created successfully and will be sent asynchronously to the Prover."
        )
      )
      .out(jsonBody[PresentationStatus])
      .errorOut(basicFailureAndNotFoundAndForbidden)

  val getAllPresentations: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, PaginationInput, Option[String]),
    ErrorResponse,
    PresentationStatusPage,
    Any
  ] =
    endpoint.get
      .tag("Present Proof")
      .name("getAllPresentation")
      .summary("Gets the list of proof presentation records.")
      .description("Get the list of proof presentation records and its status that the Agent have at moment")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in("present-proof" / "presentations")
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(paginationInput)
      .in(thidInput)
      .out(statusCode(StatusCode.Ok).description("The list of proof presentation records."))
      .out(jsonBody[PresentationStatusPage])
      .errorOut(basicFailuresAndForbidden)

  val getPresentation
      : Endpoint[(ApiKeyCredentials, JwtCredentials), (RequestContext, UUID), ErrorResponse, PresentationStatus, Any] =
    endpoint.get
      .tag("Present Proof")
      .name("getPresentation")
      .summary(
        "Gets an existing proof presentation record by its unique identifier. " +
          "More information on the error can be found in the response body."
      )
      .description("Returns an existing presentation record by id.")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "present-proof" / "presentations" / path[UUID]("presentationId").description(
          "The unique identifier of the presentation record."
        )
      )
      .out(statusCode(StatusCode.Ok).description("The proof presentation record."))
      .out(jsonBody[PresentationStatus])
      .errorOut(basicFailureAndNotFoundAndForbidden)

  val updatePresentation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, UUID, RequestPresentationAction),
    ErrorResponse,
    PresentationStatus,
    Any
  ] =
    endpoint.patch
      .tag("Present Proof")
      .name("updatePresentation")
      .summary(
        "Updates the proof presentation record matching the unique identifier, " +
          "with the specific action to perform."
      )
      .description("Accept or reject presentation of proof request.")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "present-proof" / "presentations" / path[UUID]("presentationId").description(
          "The unique identifier of the presentation record."
        )
      )
      .in(jsonBody[RequestPresentationAction].description("The action to perform on the proof presentation record."))
      .out(statusCode(StatusCode.Ok).description("The proof presentation record was successfully updated."))
      .out(jsonBody[PresentationStatus])
      .errorOut(basicFailureAndNotFoundAndForbidden)

  val createOOBRequestPresentationInvitation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, RequestPresentationInput),
    ErrorResponse,
    PresentationStatus,
    Any
  ] =
    endpoint.post
      .tag("Present Proof")
      .name("createOOBRequestPresentationInvitation")
      .summary(
        "As a Verifier, create a new OOB Invitation as proof presentation request that can be delivered out-of-band to a invitee/prover."
      )
      .description("""
                     |Create a new presentation request invitation that can be delivered out-of-band to a peer Agent, regardless of whether it resides in Cloud Agent or edge environment.
                     |The generated invitation adheres to the DIDComm Messaging v2.0 - [Out of Band Messages](https://identity.foundation/didcomm-messaging/spec/v2.0/#out-of-band-messages) specification [section 9.5.4](https://identity.foundation/didcomm-messaging/spec/v2.0/#invitation).
                     |The <b>from</b> field of the out-of-band invitation message contains a freshly generated Peer DID that complies with the [did:peer:2](https://identity.foundation/peer-did-method-spec/#generating-a-didpeer2) specification.
                     |This Peer DID includes the 'uri' location of the DIDComm messaging service, essential for the prover's subsequent execution of the connection flow.
                     |In the Agent database, the created presentation record has an initial state set to `InvitationGenerated`.
                     |The invitation is in the form of a presentation request (as described https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md), which is included as an attachment in the OOB DIDComm message sent to the invitee/prover.
                     |""".stripMargin)
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in("present-proof" / "presentations" / "invitation")
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(jsonBody[RequestPresentationInput].description("The present proof creation request."))
      .out(
        statusCode(StatusCode.Created).description(
          "The proof presentation request invitation was created successfully and that can be delivered as out-of-band to a peer Agent.."
        )
      )
      .out(jsonBody[PresentationStatus])
      .errorOut(basicFailureAndNotFoundAndForbidden)

  val acceptRequestPresentationInvitation: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, AcceptRequestPresentationInvitation),
    ErrorResponse,
    PresentationStatus,
    Any
  ] =
    endpoint.post
      .tag("Present Proof")
      .name("acceptRequestPresentationInvitation")
      .summary(
        "Decode the invitation extract Request Presentation and Create the proof presentation record with RequestReceived state."
      )
      .description("Accept Invitation for request presentation")
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "present-proof" / "presentations" / "accept-invitation"
      )
      .in(
        jsonBody[AcceptRequestPresentationInvitation].description(
          "The action to perform on the proof presentation request invitation."
        )
      )
      .out(statusCode(StatusCode.Ok).description("The proof presentation record was successfully updated."))
      .out(jsonBody[PresentationStatus])
      .errorOut(basicFailureAndNotFoundAndForbidden)
}
