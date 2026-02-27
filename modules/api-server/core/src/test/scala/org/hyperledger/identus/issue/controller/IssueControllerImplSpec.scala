package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.service.{ManagedDIDService, MockManagedDIDService}
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.castor.core.model.did.{DIDData, DIDMetadata, PrismDIDOperation, VerificationRelationship}
import org.hyperledger.identus.castor.core.service.MockDIDService
import org.hyperledger.identus.connect.core.model.ConnectionRecord
import org.hyperledger.identus.connect.core.service.MockConnectionService
import org.hyperledger.identus.container.util.MigrationAspects.migrate
import org.hyperledger.identus.iam.authentication.AuthenticatorWithAuthZ
import org.hyperledger.identus.issue.controller.http.{
  AcceptCredentialOfferRequest,
  CreateIssueCredentialRecordRequest,
  IssueCredentialRecordPage
}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.connection.ConnectionResponse
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import org.hyperledger.identus.pollux.core.model.{CredentialFormat, DidCommID, IssueCredentialRecord}
import org.hyperledger.identus.pollux.core.repository.CredentialDefinitionRepositoryInMemory
import org.hyperledger.identus.pollux.core.service.{CredentialDefinitionServiceImpl, MockCredentialService}
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.shared.models.{KeyId, WalletId}
import sttp.client3.{basicRequest, UriContext}
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json
import zio.mock.Expectation
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID

object IssueControllerImplSpec extends ZIOSpecDefault with IssueControllerTestTools {
  val json: String =
    """{
      |        "emailAddress": "alice@wonderland.com",
      |        "givenName": "Alice",
      |        "familyName": "Wonderland",
      |        "dateOfIssuance": "2020-11-13T20:20:39+00:00",
      |        "drivingLicenseID": "12345",
      |        "drivingClass": "3"
      |    }""".stripMargin

  val createIssueCredentialRecordRequest: CreateIssueCredentialRecordRequest = CreateIssueCredentialRecordRequest(
    validityPeriod = Some(24.5),
    schemaId = Some("mySchemaId"),
    credentialDefinitionId = Some(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
    credentialFormat = Some("JWT"),
    claims = json.fromJson[Json].toOption,
    automaticIssuance = Some(true),
    issuingDID = Option(
      "did:prism:332518729a7b7805f73a788e0944802527911901d9b7c16152281be9bc62d944:CosBCogBEkkKFW15LWtleS1hdXRoZW50aWNhdGlvbhAESi4KCXNlY3AyNTZrMRIhAuYoRIefsLhkvYwHz8gDtkG2b0kaZTDOLj_SExWX1fOXEjsKB21hc3RlcjAQAUouCglzZWNwMjU2azESIQLOzab8f0ibt1P0zdMfoWDQTSlPc8_tkV9Jk5BBsXB8fA"
    ),
    issuingKid = Some(KeyId("some_kid_id")),
    connectionId = Some(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
  )
  private val issueCredentialRecord = IssueCredentialRecord(
    DidCommID(),
    Instant.now,
    None,
    DidCommID(),
    None,
    None,
    None,
    CredentialFormat.JWT,
    invitation = None,
    IssueCredentialRecord.Role.Issuer,
    None,
    None,
    None,
    None,
    IssueCredentialRecord.ProtocolState.OfferPending,
    None,
    None,
    None,
    None,
    None,
    None,
    5,
    None,
    None
  )

  val connectionResponse = ConnectionResponse(
    id = "b7878bfc-16d5-49dd-a443-4e87a3c4c8c6",
    from = DidId(
      "did:peer:2.Ez6LSpwvTbwvMF5xtSZ6uNoZvWNcPGx1J2ziuais63CpB1UDe.Vz6MkmutH2XW9ybLtSyYRvYcyUbUPWneev6oVu9zfoEmFxQ2y.SeyJ0IjoiZG0iLCJzIjoiaHR0cDovL2hvc3QuZG9ja2VyLmludGVybmFsOjgwODAvZGlkY29tbSIsInIiOltdLCJhIjpbImRpZGNvbW0vdjIiXX0"
    ),
    to = DidId(
      "did:peer:2.Ez6LSr1TzNDH5S4GMtn1ELG6P6xBdLcFxQ8wBaZCn8bead7iK.Vz6MknkPqgbvK4c7GhsKzi2EyBV4rZbvtygJqxM4Eh8EF5DGB.SeyJyIjpbImRpZDpwZWVyOjIuRXo2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5uby5WejZNa2pqQ3F5SkZUSHFpWGtZUndYcVhTZlo2WWtVMjFyMzdENkFCN1hLMkhZNXQyLlNleUpwWkNJNkltNWxkeTFwWkNJc0luUWlPaUprYlNJc0luTWlPaUpvZEhSd2N6b3ZMMjFsWkdsaGRHOXlMbkp2YjNSemFXUXVZMnh2ZFdRaUxDSmhJanBiSW1ScFpHTnZiVzB2ZGpJaVhYMCM2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5ubyJdLCJzIjoiaHR0cHM6Ly9tZWRpYXRvci5yb290c2lkLmNsb3VkIiwiYSI6WyJkaWNvbW0vdjIiXSwidCI6ImRtIn0"
    ),
    thid = None,
    pthid = Some("52dc177a-05dc-4deb-ab57-ac9d5e3ff10c"),
    body = ConnectionResponse.Body(
      goal_code = Some("connect"),
      goal = Some("Establish a trust connection between two peers"),
      accept = Some(Seq.empty)
    ),
  )
  private val record = ConnectionRecord(
    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
    Instant.now,
    None,
    UUID.randomUUID().toString,
    None,
    None,
    None,
    ConnectionRecord.Role.Inviter,
    ConnectionRecord.ProtocolState.ConnectionResponseSent,
    Invitation(from = DidId("did:peer:INVITER"), body = Invitation.Body(None, None, Nil)),
    None,
    Some(connectionResponse),
    5,
    None,
    None,
    WalletId.fromUUID(UUID.randomUUID)
  )
  val acceptCredentialOfferRequest = AcceptCredentialOfferRequest(
    Some(
      "did:prism:332518729a7b7805f73a788e0944802527911901d9b7c16152281be9bc62d944"
    ),
    None
  )
  val acceptCredentialOfferRequest2 = AcceptCredentialOfferRequest(
    Some(
      "did:prism:332518729a7b7805f73a788e0944802527911901d9b7c16152281be9bc62d944:CosBCogBEkkKFW15LWtleS1hdXRoZW50aWNhdGlvbhAESi4KCXNlY3AyNTZrMRIhAuYoRIefsLhkvYwHz8gDtkG2b0kaZTDOLj_SExWX1fOXEjsKB21hc3RlcjAQAUouCglzZWNwMjU2azESIQLOzab8f0ibt1P0zdMfoWDQTSlPc8_tkV9Jk5BBsXB8fA"
    ),
    None
  )
  val acceptCredentialOfferRequestWithKeyId = AcceptCredentialOfferRequest(
    Some(
      "did:prism:332518729a7b7805f73a788e0944802527911901d9b7c16152281be9bc62d944"
    ),
    Some("key-id-auth")
  )
  private val mockConnectionServiceLayer =
    MockConnectionService.FindById(
      assertion = Assertion.anything,
      result = Expectation.value(Some(record))
    )

  private def createDid(rel: VerificationRelationship): (DIDMetadata, DIDData) = {
    val x = MockDIDService.createDID(rel)
    (x._3, x._4)
  }

  private def mockDIDServiceExpectations(relationship: VerificationRelationship) = {
    val (x: DIDMetadata, y: DIDData) = createDid(relationship)
    MockDIDService.resolveDIDExpectation(x, y)
  }
  private val mockCredentialServiceExpectations =
    MockCredentialService.CreateJWTIssueCredentialRecord(
      assertion = Assertion.anything,
      result = Expectation.value(issueCredentialRecord)
    )
  private val mockCredentialServiceExpectationsAcceptCredentialOffer = MockCredentialService.AcceptCredentialOffer(
    assertion = Assertion.anything,
    result =
      Expectation.value(issueCredentialRecord.copy(protocolState = IssueCredentialRecord.ProtocolState.RequestPending))
  )

  private val mockManagedDIDServiceExpectations: Expectation[ManagedDIDService] = MockManagedDIDService
    .GetManagedDIDState(
      assertion = Assertion.anything,
      result = Expectation.value(
        Some(
          ManagedDIDState(
            PrismDIDOperation.Create(Nil, Nil, Nil),
            0,
            PublicationState.Published(scala.collection.immutable.ArraySeq.empty)
          )
        )
      )
    )

  private val credentialDefinitionServiceLayer =
    CredentialDefinitionRepositoryInMemory.layer
      >+> GenericSecretStorageInMemory.layer >+>
      ResourceUrlResolver.layer >>> CredentialDefinitionServiceImpl.layer

  val baseLayer =
    MockManagedDIDService.empty
      >+> MockDIDService.empty
      >+> MockCredentialService.empty
      >+> MockConnectionService.empty
      >+> credentialDefinitionServiceLayer

  def spec = (httpErrorResponses @@ migrate(
    schema = "public",
    paths = "classpath:sql/pollux"
  )).provideLayer(baseLayer >+> testEnvironmentLayer)

  private val httpErrorResponses = suite("IssueControllerImp http failure cases")(
    test("provide incorrect recordId to endpoint") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialBadRequestResponse <- basicRequest
          .post(uri"${issueUriBase}/records/12345/accept-offer")
          .body(AcceptCredentialOfferRequest(Some("subjectId"), None).toJsonPretty)
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        isItABadRequestStatusCode = assert(response.code)(equalTo(StatusCode.BadRequest))
        theBodyWasParsedFromJsonAsABadRequest = assert(response.body)(
          isRight(
            isSubtype[ErrorResponse](
              hasField("status", _.status, equalTo(StatusCode.BadRequest.code))
            )
          )
        )
      } yield isItABadRequestStatusCode && theBodyWasParsedFromJsonAsABadRequest
    },
    test("createCredentialOffer for issuer PrismDid without AssertionMethod should return 400") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialBadRequestResponse <- basicRequest
          .post(uri"${issueUriBase}/credential-offers")
          .body(createIssueCredentialRecordRequest.toJsonPretty)
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        isItABadRequestStatusCode = assert(response.code)(equalTo(StatusCode.BadRequest))
        theBodyWasParsedFromJsonAsABadRequest = assert(response.body)(
          isRight(
            isSubtype[ErrorResponse](
              hasField("status", _.status, equalTo(StatusCode.BadRequest.code))
            )
          )
        )
      } yield isItABadRequestStatusCode && theBodyWasParsedFromJsonAsABadRequest
    }.provideLayer(
      baseLayer
        ++ mockConnectionServiceLayer.toLayer
        ++ mockManagedDIDServiceExpectations.toLayer
        ++ mockDIDServiceExpectations(VerificationRelationship.Authentication).toLayer
        >+> testEnvironmentLayer
    ),
    test("createCredentialOffer for issuer PrismDid with AssertionMethod should return 201") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialBadRequestResponse <- basicRequest
          .post(uri"${issueUriBase}/credential-offers")
          .body(createIssueCredentialRecordRequest.toJsonPretty)
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        isSuccessRequestStatusCode = assert(response.code)(equalTo(StatusCode.Created))

      } yield isSuccessRequestStatusCode
    }.provideLayer(
      baseLayer
        ++ mockConnectionServiceLayer.toLayer
        ++ mockManagedDIDServiceExpectations.toLayer
        ++ mockDIDServiceExpectations(VerificationRelationship.AssertionMethod).toLayer
        ++ mockCredentialServiceExpectations.toLayer
        >+> testEnvironmentLayer
    ),
    test("AcceptCredentialOffer for Holder PrismDid with Authentication should return 200") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialPageResponse <- basicRequest
          .post(uri"${issueUriBase}/records/123e4567-e89b-12d3-a456-426614174000/accept-offer")
          .body(acceptCredentialOfferRequest.toJsonPretty)
          .response(asJsonAlways[IssueCredentialRecordPage])
          .send(backend)

        isSuccessRequestStatusCode = assert(response.code)(equalTo(StatusCode.Ok))

      } yield isSuccessRequestStatusCode
    }.provideLayer(
      baseLayer
        ++ mockManagedDIDServiceExpectations.toLayer
        ++ mockDIDServiceExpectations(VerificationRelationship.Authentication).toLayer
        ++ mockCredentialServiceExpectationsAcceptCredentialOffer.toLayer
        >+> testEnvironmentLayer
    ),
    test("AcceptCredentialOffer for Holder PrismDid without Authentication key should return 400") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialBadRequestResponse <- basicRequest
          .post(uri"${issueUriBase}/records/123e4567-e89b-12d3-a456-426614174000/accept-offer")
          .body(acceptCredentialOfferRequest.toJsonPretty)
          .response(asJsonAlways[ErrorResponse])
          .send(backend)

        isItABadRequestStatusCode = assert(response.code)(equalTo(StatusCode.BadRequest))
        theBodyWasParsedFromJsonAsABadRequest = assert(response.body)(
          isRight(
            isSubtype[ErrorResponse](
              hasField("status", _.status, equalTo(StatusCode.BadRequest.code))
            )
          )
        )
      } yield isItABadRequestStatusCode && theBodyWasParsedFromJsonAsABadRequest
    }.provideLayer(
      baseLayer
        ++ mockManagedDIDServiceExpectations.toLayer
        ++ mockDIDServiceExpectations(VerificationRelationship.AssertionMethod).toLayer
        >+> testEnvironmentLayer
    ),
    test("AcceptCredentialOffer with keyId for Holder PrismDid with Authentication should return 200") {
      for {
        issueControllerService <- ZIO.service[IssueController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        backend = httpBackend(issueControllerService, authenticator)
        response: IssueCredentialPageResponse <- basicRequest
          .post(uri"${issueUriBase}/records/123e4567-e89b-12d3-a456-426614174000/accept-offer")
          .body(acceptCredentialOfferRequestWithKeyId.toJsonPretty)
          .response(asJsonAlways[IssueCredentialRecordPage])
          .send(backend)

        isSuccessRequestStatusCode = assert(response.code)(equalTo(StatusCode.Ok))

      } yield isSuccessRequestStatusCode
    }.provideLayer(
      baseLayer
        ++ mockManagedDIDServiceExpectations.toLayer
        ++ mockDIDServiceExpectations(VerificationRelationship.Authentication).toLayer
        ++ mockCredentialServiceExpectationsAcceptCredentialOffer.toLayer
        >+> testEnvironmentLayer
    ),
  )
}
