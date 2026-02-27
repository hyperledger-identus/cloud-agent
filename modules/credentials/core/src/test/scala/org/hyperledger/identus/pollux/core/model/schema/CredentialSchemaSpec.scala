package org.hyperledger.identus.pollux.core.model.schema

import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError
import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError.CredentialSchemaValidationError
import org.hyperledger.identus.pollux.core.model.schema.`type`.{AnoncredSchemaType, CredentialJsonSchemaType}
import org.hyperledger.identus.pollux.core.model.schema.`type`.anoncred.AnoncredSchemaSerDesV1
import org.hyperledger.identus.pollux.core.model.schema.AnoncredSchemaTypeSpec.test
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.shared.json.JsonSchemaError.JsonValidationErrors
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.{assertZIO, Assertion, Spec, TestEnvironment, ZIOSpecDefault}
import zio.test.Assertion.*
import zio.Scope

import java.time.OffsetDateTime
import java.util.UUID

object CredentialSchemaSpec extends ZIOSpecDefault {

  private def basicCredentialJsonSchema(innerJsonSchema: String): CredentialSchema = {
    CredentialSchema(
      guid = UUID.randomUUID(),
      id = UUID.randomUUID(),
      name = "JsonSchema",
      version = "1.0",
      author = "author",
      authored = OffsetDateTime.parse("2022-03-10T12:00:00Z"),
      tags = Seq("tag1", "tag2"),
      description = "Json Schema",
      `type` = CredentialJsonSchemaType.VC_JSON_SCHEMA_URI,
      resolutionMethod = ResourceResolutionMethod.http,
      schema = innerJsonSchema.fromJson[Json].getOrElse(Json.Null)
    )
  }

  private def basicAnonCredsSchema(innerJsonSchema: String): CredentialSchema = {
    CredentialSchema(
      guid = UUID.randomUUID(),
      id = UUID.randomUUID(),
      name = "Anoncred",
      version = "1.0",
      author = "author",
      authored = OffsetDateTime.parse("2022-03-10T12:00:00Z"),
      tags = Seq("tag1", "tag2"),
      description = "Anoncred Schema",
      `type` = AnoncredSchemaSerDesV1.version,
      resolutionMethod = ResourceResolutionMethod.http,
      schema = innerJsonSchema.fromJson[Json].getOrElse(Json.Null)
    )
  }

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("CredentialSchemaTest")(
    suite("resolveCredentialSchemaType")(
      test("should return AnoncredSchemaType for a supported schema type") {

        val schemaType = AnoncredSchemaType.`type`
        val result = CredentialSchema.resolveCredentialSchemaType(schemaType)
        assertZIO(result)(Assertion.equalTo(AnoncredSchemaType))
      },
      test("should return CredentialJsonSchemaType for a supported schema type") {
        val schemaType = CredentialJsonSchemaType.`type`
        val result = CredentialSchema.resolveCredentialSchemaType(schemaType)
        assertZIO(result)(Assertion.equalTo(CredentialJsonSchemaType))
      },
      test("should fail with UnsupportedCredentialSchemaType for an unsupported schema type") {
        val schemaType = "UnsupportedSchemaType"
        val result = CredentialSchema.resolveCredentialSchemaType(schemaType)
        assertZIO(result.exit)(
          fails(
            isSubtype[CredentialSchemaError.UnsupportedCredentialSchemaType](
              hasField("message", _.userFacingMessage, equalTo(s"Unsupported credential schema type: $schemaType"))
            )
          )
        )
      }
    ),
    suite("validateAnonCredsSchema")(
      test("should validate a correct schema") {
        val credentialSchema = basicAnonCredsSchema(
          """
            |{
            |  "name": "Anoncred",
            |  "version": "1.0",
            |  "attrNames": ["attr1", "attr2"],
            |  "issuerId": "issuer"
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema))(isUnit)
      },
      test("should fail for attrName not unique") {
        val credentialSchema = basicAnonCredsSchema(
          """
            |{
            |  "name": "Anoncred",
            |  "version": "1.0",
            |  "attrNames": ["attr1", "attr1"],
            |  "issuerId": "issuer"
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$.attrNames: the items in the array must be unique")
          )
        )
      }
    ),
    suite("validateJwtCredentialSchema")(
      test("should validate a correct basic single-level schema") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$id": "https://example.com/driving-license-1.0.0",
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "description": "Driving License",
            |  "type": "object",
            |  "properties": {
            |    "emailAddress": {
            |      "type": "string",
            |      "format": "email"
            |    },
            |    "givenName": {
            |      "type": "string"
            |    },
            |    "familyName": {
            |      "type": "string"
            |    },
            |    "dateOfIssuance": {
            |      "type": "string",
            |      "format": "date-time"
            |    },
            |    "drivingLicenseID": {
            |      "type": "string"
            |    },
            |    "drivingClass": {
            |      "type": "integer"
            |    }
            |  },
            |  "required": [
            |    "emailAddress",
            |    "familyName",
            |    "dateOfIssuance",
            |    "drivingLicenseID",
            |    "drivingClsass"
            |  ],
            |  "additionalProperties": true
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema))(isUnit)
      },
      test("should validate a correct complex multi-level schema") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$id": "https://example.com/driving-license-1.0.0",
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "description": "Driving License",
            |  "type": "object",
            |  "properties": {
            |    "givenName": {
            |      "type": "string",
            |      "minLength": 10,
            |      "maxLength": 50
            |    },
            |    "familyName": {
            |      "type": "string",
            |      "pattern": "^[A-Z][A-Za-z. ]*[a-z]$"
            |    },
            |    "address": {
            |      "type": "object",
            |      "properties": {
            |        "street": {
            |          "type": "string",
            |          "minLength": 5,
            |          "maxLength": 150
            |        },
            |        "postalCode": {
            |          "type": "integer",
            |          "minimum": 1000,
            |          "maximum": 9999,
            |          "enum": [1001, 2000, 3000, 2500]
            |        },
            |        "city": {
            |          "type": "string",
            |          "minLength": 5,
            |          "maxLength": 45
            |        },
            |        "country": {
            |          "type": "string",
            |          "enum": ["BE", "GB", "US", "FR"]
            |        }
            |      }
            |    }
            |  },
            |  "required": [
            |    "familyName",
            |    "address"
            |  ],
            |  "additionalProperties": false
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema))(isUnit)
      },
      test("should fail meta-schema validation on missing '$schema'") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "type": "object",
            |  "properties": {
            |    "familyName": {
            |      "type": "string"
            |    }
            |  }
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$: required property '$schema' not found")
          )
        )
      },
      test("should fail meta-schema validation on missing 'type'") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "properties": {
            |    "familyName": {
            |      "type": "string"
            |    }
            |  }
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$: required property 'type' not found")
          )
        )
      },
      test("should fail meta-schema validation on invalid 'type' value") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "type": "invalid",
            |  "properties": {
            |    "familyName": {
            |      "type": "string"
            |    }
            |  }
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$.type: does not have a value in the enumeration [object]")
          )
        )
      },
      test("should fail meta-schema validation on invalid '$schema' value") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$schema": "https://json-schema.org/draft/1990-12/schema",
            |  "type": "object",
            |  "properties": {
            |    "familyName": {
            |      "type": "string"
            |    }
            |  }
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$.$schema: does not have a value in the enumeration [https://json-schema.org/draft/2020-12/schema]")
          )
        )
      },
      test("should fail meta-schema validation on missing 'properties'") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "type": "object"
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq("$: required property 'properties' not found")
          )
        )
      },
      test("should fail meta-schema validation on additional unknown properties") {
        val credentialSchema = basicCredentialJsonSchema(
          """
            |{
            |  "$schema": "https://json-schema.org/draft/2020-12/schema",
            |  "type": "object",
            |  "properties": {
            |    "familyName": {
            |      "type": "string"
            |    }
            |  },
            |  "unknownProperty": {
            |    "type": "string"
            |  }
            |}
            |""".stripMargin
        )
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          failsWithJsonValidationErrors(
            Seq(
              "$: property 'unknownProperty' is not defined in the schema and the schema does not allow additional properties"
            )
          )
        )
      }
    ),
    suite("validateUnsupportedSchema")(
      test("should fail validation on unsupported schema type") {
        val schemaType = "UnsupportedSchemaType"
        val credentialSchema = basicAnonCredsSchema(
          """
            |{
            |  "name": "Anoncred",
            |  "version": "1.0",
            |  "attrNames": ["attr1", "attr2"],
            |  "issuerId": "issuer"
            |}
            |""".stripMargin
        ).copy(`type` = schemaType)
        assertZIO(CredentialSchema.validateCredentialSchema(credentialSchema).exit)(
          fails(
            isSubtype[CredentialSchemaError.UnsupportedCredentialSchemaType](
              hasField("message", _.userFacingMessage, equalTo(s"Unsupported credential schema type: $schemaType"))
            )
          )
        )
      }
    )
  )

  def failsWithJsonValidationErrors(errorMessages: Iterable[String]) = {
    fails(
      isSubtype[CredentialSchemaValidationError](
        hasField(
          "schemaError",
          _.schemaError,
          isSubtype[JsonValidationErrors](
            hasField("errors", _.errors, hasSameElementsDistinct(errorMessages))
          )
        )
      )
    )
  }
}
