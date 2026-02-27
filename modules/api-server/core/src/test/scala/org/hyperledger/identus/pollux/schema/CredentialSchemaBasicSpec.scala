package org.hyperledger.identus.pollux.schema

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.container.util.MigrationAspects.*
import org.hyperledger.identus.iam.authentication.AuthenticatorWithAuthZ
import org.hyperledger.identus.pollux.core.model.schema.`type`.CredentialJsonSchemaType
import org.hyperledger.identus.pollux.credentialschema.*
import org.hyperledger.identus.pollux.credentialschema.controller.CredentialSchemaController
import org.hyperledger.identus.pollux.credentialschema.http.{CredentialSchemaInput, CredentialSchemaResponse}
import sttp.client3.basicRequest
import sttp.client3.ziojson.*
import sttp.model.StatusCode
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.ZIO.*

import java.util.UUID

object CredentialSchemaBasicSpec extends ZIOSpecDefault with CredentialSchemaTestTools:

  val jsonSchema =
    """
      |{
      |    "$schema": "https://json-schema.org/draft/2020-12/schema",
      |    "description": "Driving License",
      |    "type": "object",
      |    "properties": {
      |        "name" : {
      |          "type": "string"
      |        }
      |    },
      |    "required": [
      |        "name"
      |    ]
      |}
      |""".stripMargin

  private val schemaInput = CredentialSchemaInput(
    name = "TestSchema",
    version = "1.0.0",
    description = Option("schema description"),
    `type` = CredentialJsonSchemaType.`type`,
    schema = jsonSchema.fromJson[Json].getOrElse(Json.Null),
    tags = List("test"),
    author = "did:prism:557a4ef2ed0cf86fb50d91577269136b3763722ef00a72a1fb1e66895f52b6d8"
  )

  def spec = (
    schemaCreateAndGetOperationsSpec
      @@ nondeterministic @@ sequential @@ timed @@ migrateEach(
        schema = "public",
        paths = "classpath:sql/pollux"
      )
  ).provideSomeLayerShared(
    mockManagedDIDServiceLayer.toLayer >+>
      testEnvironmentLayer
  )

  private val schemaCreateAndGetOperationsSpec = {
    val backendZIO =
      for {
        controller <- ZIO.service[CredentialSchemaController]
        authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
        config <- ZIO.service[AppConfig]
      } yield httpBackend(config, controller, authenticator)

    def createSchemaResponseZIO = for {
      backend <- backendZIO
      response <- basicRequest
        .post(credentialSchemaUriBase)
        .body(schemaInput.toJsonPretty)
        .response(asJsonAlways[CredentialSchemaResponse])
        .send(backend)
    } yield response

    def getSchemaZIO(uuid: UUID) = for {
      backend <- backendZIO
      response <- basicRequest
        .get(credentialSchemaUriBase.addPath(uuid.toString))
        .response(asJsonAlways[CredentialSchemaResponse])
        .send(backend)

      fetchedSchema <- fromEither(response.body)
    } yield fetchedSchema

    def getRawSchemaZIO(uuid: UUID) = for {
      backend <- backendZIO
      response <- basicRequest
        .get(credentialSchemaUriBase.addPath(uuid.toString).addPath("schema"))
        .response(asJson)
        .send(backend)

      fetchedSchema <- fromEither(response.body)
    } yield fetchedSchema

    suite("schema-registry create and get by ID operations logic")(
      test("create the new schema") {
        for {
          response <- createSchemaResponseZIO
          statusCodeIs201 = assert(response.code)(equalTo(StatusCode.Created))

          credentialSchema <- fromEither(response.body)
          actualFields = CredentialSchemaInput(
            name = credentialSchema.name,
            version = credentialSchema.version,
            description = Option(credentialSchema.description),
            `type` = credentialSchema.`type`,
            schema = credentialSchema.schema,
            tags = credentialSchema.tags,
            author = credentialSchema.author
          )

          credentialSchemaIsCreated = assert(schemaInput)(equalTo(actualFields))

          fetchedSchema <- getSchemaZIO(credentialSchema.guid)

          fetchedRawSchema <- getRawSchemaZIO(credentialSchema.guid)

          credentialSchemaIsFetched = assert(fetchedSchema)(equalTo(credentialSchema))
          rawCredentialSchemaIsFetched = assert(fetchedRawSchema)(equalTo(credentialSchema.schema))

        } yield statusCodeIs201 && credentialSchemaIsCreated && credentialSchemaIsFetched && rawCredentialSchemaIsFetched
      },
      test("get the schema by the wrong id") {
        for {
          backend <- backendZIO
          uuid = UUID.randomUUID()

          response <- basicRequest
            .get(credentialSchemaUriBase.addPath(uuid.toString))
            .response(asJsonAlways[ErrorResponse])
            .send(backend)
        } yield assert(response.code)(equalTo(StatusCode.NotFound))
      }
    )
  }
