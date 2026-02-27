package org.hyperledger.identus.pollux.credentialdefinition

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, Entity}
import org.hyperledger.identus.agent.walletapi.storage.GenericSecretStorage
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.container.util.MigrationAspects.*
import org.hyperledger.identus.iam.authentication.AuthenticatorWithAuthZ
import org.hyperledger.identus.pollux.core.model.secret.CredentialDefinitionSecret
import org.hyperledger.identus.pollux.core.service.serdes.{
  PrivateCredentialDefinitionSchemaSerDesV1,
  ProofKeyCredentialDefinitionSchemaSerDesV1,
  PublicCredentialDefinitionSerDesV1
}
import org.hyperledger.identus.pollux.credentialdefinition.controller.CredentialDefinitionController
import org.hyperledger.identus.pollux.credentialdefinition.http.{
  CredentialDefinitionInput,
  CredentialDefinitionResponse
}
import sttp.client3.basicRequest
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import zio.*
import zio.json.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.ZIO.*

import java.util.UUID

object CredentialDefinitionBasicSpec extends ZIOSpecDefault with CredentialDefinitionTestTools:

  val definitionString =
    """
      |{
      |   "credentialSubject": {
      |     "emailAddress": "alice@wonderland.com",
      |     "givenName": "Alice",
      |     "familyName": "Wonderland",
      |     "dateOfIssuance": "2000-01-01T10:00:00Z",
      |     "drivingLicenseID": "12345",
      |     "drivingClass": 5
      |   }
      |}
      |""".stripMargin

  private val credentialDefinitionInput =
    CredentialDefinitionInput(
      name = "TestCredentialDefinition",
      version = "1.0.0",
      description = Option("Credential Definition Description"),
      schemaId = "resource:///anoncred-schema-example.json",
      tag = "test",
      author = "did:prism:557a4ef2ed0cf86fb50d91577269136b3763722ef00a72a1fb1e66895f52b6d8",
      signatureType = "CL",
      supportRevocation = false
    )

  def spec = (
    credentialDefinitionCreateAndGetOperationsSpec
      @@ nondeterministic @@ sequential @@ timed @@ migrateEach(
        schema = "public",
        paths = "classpath:sql/pollux"
      )
  ).provideSomeLayerShared(mockManagedDIDServiceLayer.toLayer >+> testEnvironmentLayer)

  private val credentialDefinitionCreateAndGetOperationsSpec = {
    val backendZIO =
      for {
        controller <- ZIO.service[CredentialDefinitionController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        config <- ZIO.service[AppConfig]
      } yield httpBackend(config, controller, authenticator)

    def createCredentialDefinitionResponseZIO = for {
      backend <- backendZIO
      response <- basicRequest
        .post(credentialDefinitionUriBase)
        .body(credentialDefinitionInput.toJsonPretty)
        .response(asJsonAlways[CredentialDefinitionResponse])
        .send(backend)
    } yield response

    def getCredentialDefinitionResponseZIO(uuid: UUID) = for {
      backend <- backendZIO
      response <- basicRequest
        .get(credentialDefinitionUriBase.addPath(uuid.toString))
        .response(asJsonAlways[CredentialDefinitionResponse])
        .send(backend)
    } yield response

    suite("credential-definition-registry create and get by ID operations logic")(
      test("create the new credential definition") {
        for {
          response <- createCredentialDefinitionResponseZIO
          statusCodeIs201 = assert(response.code)(equalTo(StatusCode.Created))

          credentialDefinition <- fromEither(response.body)
          actualFields = CredentialDefinitionInput(
            name = credentialDefinition.name,
            version = credentialDefinition.version,
            description = Some(credentialDefinition.description),
            tag = credentialDefinition.tag,
            author = credentialDefinition.author,
            schemaId = credentialDefinition.schemaId,
            signatureType = credentialDefinition.signatureType,
            supportRevocation = credentialDefinition.supportRevocation
          )

          credentialDefinitionIsCreated = assert(credentialDefinitionInput)(equalTo(actualFields))

          getCredentialDefinitionResponse <- getCredentialDefinitionResponseZIO(credentialDefinition.guid)
          fetchedCredentialDefinition <- fromEither(getCredentialDefinitionResponse.body)
          credentialDefinitionIsFetched = assert(fetchedCredentialDefinition)(equalTo(credentialDefinition))
          maybeValidPublicDefinition <- PublicCredentialDefinitionSerDesV1.schemaSerDes.validate(
            fetchedCredentialDefinition.definition.toString()
          )
          assertValidPublicDefinition = assert(maybeValidPublicDefinition)(Assertion.isUnit)
          maybeValidKeyCorrectnessProof <- ProofKeyCredentialDefinitionSchemaSerDesV1.schemaSerDes.validate(
            fetchedCredentialDefinition.keyCorrectnessProof.toString()
          )
          assertValidKeyCorrectnessProof = assert(maybeValidKeyCorrectnessProof)(Assertion.isUnit)
          storage <- ZIO.service[GenericSecretStorage]
          maybeDidSecret <- storage
            .get[UUID, CredentialDefinitionSecret](fetchedCredentialDefinition.guid)
            .provideSomeLayer(Entity.Default.wacLayer)
          maybeValidPrivateDefinitionZIO = maybeDidSecret match {
            case Some(didSecret) =>
              PrivateCredentialDefinitionSchemaSerDesV1.schemaSerDes.validate(didSecret.json.toString())
            case None => ZIO.unit
          }
          maybeValidPrivateDefinition <- maybeValidPrivateDefinitionZIO
          assertValidPrivateDefinition = assert(maybeValidPrivateDefinition)(Assertion.isUnit)
        } yield statusCodeIs201 &&
          credentialDefinitionIsCreated &&
          credentialDefinitionIsFetched &&
          assertValidPublicDefinition &&
          assertValidKeyCorrectnessProof &&
          assertValidPrivateDefinition
      },
      test("get the credential definition by the wrong id") {
        for {
          backend <- backendZIO
          uuid = UUID.randomUUID()

          response <- basicRequest
            .get(credentialDefinitionUriBase.addPath(uuid.toString))
            .response(asJsonAlways[ErrorResponse])
            .send(backend)
        } yield assert(response.code)(equalTo(StatusCode.NotFound))
      }
    )
  }
