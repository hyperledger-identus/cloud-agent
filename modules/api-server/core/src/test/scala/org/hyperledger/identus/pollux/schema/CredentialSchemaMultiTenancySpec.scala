package org.hyperledger.identus.pollux.schema

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.container.util.MigrationAspects.*
import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaUpdateError
import org.hyperledger.identus.pollux.core.model.schema.`type`.CredentialJsonSchemaType
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.service.{CredentialSchemaService, CredentialSchemaServiceImpl}
import org.hyperledger.identus.pollux.sql.repository.JdbcCredentialSchemaRepository
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.ZIO.*

import java.util.UUID

object CredentialSchemaMultiTenancySpec extends ZIOSpecDefault with CredentialSchemaTestTools:

  val Alice = Entity(name = "Alice", walletId = UUID.fromString("557a4ef2-ed0c-f86f-b50d-91577269136b"))
  val Bob = Entity(name = "Bob", walletId = UUID.fromString("3763722e-f00a-72a1-fb1e-66895f52b6d8"))

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

  private def schemaInput(author: String) = CredentialSchema.Input(
    name = "TestSchema",
    version = "1.0.0",
    description = "schema description",
    authored = None,
    `type` = CredentialJsonSchemaType.`type`,
    schema = jsonSchema.fromJson[Json].getOrElse(Json.Null),
    tags = List("test"),
    author = author
  )

  val serviceLayer = ZLayer.make[CredentialSchemaService & PostgreSQLContainer](
    contextAwareTransactorLayer,
    systemTransactorLayer,
    pgContainerLayer,
    JdbcCredentialSchemaRepository.layer,
    CredentialSchemaServiceImpl.layer
  )

  def spec = (
    multitenanySchemaSpec
      @@ nondeterministic @@ sequential @@ timed @@ migrateEach(
        schema = "public",
        paths = "classpath:sql/pollux"
      )
  ).provideSomeLayerShared(serviceLayer)

  val multitenanySchemaSpec = suite("Multitenancy CredentialSchema spec")(
    test("Alice & Bob schemas are isolated")(
      for {
        schemaInputA <- ZIO.succeed(schemaInput(Alice.name))
        schemaInputB <- ZIO.succeed(schemaInput(Bob.name))

        service <- ZIO.service[CredentialSchemaService]

        createdSchemaA <- service.create(schemaInputA).provideLayer(Alice.wacLayer)
        fetchedSchemaA <- service.getByGUID(createdSchemaA.guid).provideLayer(Alice.wacLayer)

        createdSchemaB <- service.create(schemaInputB).provideLayer(Bob.wacLayer)
        fetchedSchemaB <- service.getByGUID(createdSchemaB.guid).provideLayer(Bob.wacLayer)

        allSchemasA <- service.lookup(CredentialSchema.Filter(), skip = 0, limit = 10).provideLayer(Alice.wacLayer)
        allSchemasB <- service.lookup(CredentialSchema.Filter(), skip = 0, limit = 10).provideLayer(Bob.wacLayer)

        aliceCannotAccessBobsVCSchema = assertTrue(allSchemasA.count == 1L)
        bobCannotAccessAlicesVCSchema = assertTrue(allSchemasB.count == 1L)

        updatedSchemaA <- service
          .update(createdSchemaA.id, schemaInputA.copy(name = "UpdatedSchema", version = "1.1"))
          .provideLayer(Alice.wacLayer)
        updatedSchemaB <- service
          .update(createdSchemaB.id, schemaInputB.copy(name = "UpdatedSchema", version = "1.1"))
          .provideLayer(Bob.wacLayer)

        notFoundSchemaAError <- service
          .update(createdSchemaA.id, schemaInputA.copy(name = "UpdatedSchema2", version = "1.2"))
          .provideLayer(Bob.wacLayer)
          .exit

        notFoundSchemaBError <- service
          .update(createdSchemaB.id, schemaInputB.copy(name = "UpdatedSchema2", version = "1.2"))
          .provideLayer(Alice.wacLayer)
          .exit

        aliceCannotUpdateBobsVCSchema = assert(notFoundSchemaAError)(
          fails(isSubtype[CredentialSchemaUpdateError](anything))
        )
        bobCannotUpdateAlicesVCSchema = assert(notFoundSchemaBError)(
          fails(isSubtype[CredentialSchemaUpdateError](anything))
        )

        fetchedSchemaAbyB <- service.getByGUID(updatedSchemaA.guid).provideLayer(Bob.wacLayer)
        fetchedSchemaBbyA <- service.getByGUID(updatedSchemaB.guid).provideLayer(Alice.wacLayer)

        aliceCanAccessBobVCSchemaByGUID = assert(fetchedSchemaAbyB)(equalTo(updatedSchemaA))
        bobCanAccessAliceVCSchemaByGUID = assert(fetchedSchemaBbyA)(equalTo(updatedSchemaB))

      } yield assert(fetchedSchemaA)(equalTo(createdSchemaA)) &&
        assert(fetchedSchemaB)(equalTo(createdSchemaB)) &&
        aliceCannotAccessBobsVCSchema && bobCannotAccessAlicesVCSchema &&
        aliceCannotUpdateBobsVCSchema && bobCannotUpdateAlicesVCSchema &&
        aliceCanAccessBobVCSchemaByGUID && bobCanAccessAliceVCSchemaByGUID
    )
  )
