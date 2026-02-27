package org.hyperledger.identus.pollux.core.model.schema

import org.hyperledger.identus.pollux.core.model.schema.`type`.AnoncredSchemaType
import org.hyperledger.identus.shared.json.JsonSchemaError
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*
import zio.test.*
import zio.test.Assertion.*

import scala.util.Random

object AnoncredSchemaTypeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] = suite("AnoncredSchemaTypeTest")(
    test("should validate a correct schema") {
      val jsonSchema =
        """
          |{
          |  "name": "Anoncred",
          |  "version": "1.0",
          |  "attrNames": ["attr1", "attr2"],
          |  "issuerId": "issuer"
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema))(isUnit)
    },
    test("should validate a correct schema") {
      val jsonSchema =
        """
          |{
          |  "name": "Anoncred",
          |  "version": "1.0",
          |  "attrNames": ["attr1", "attr2"],
          |  "issuerId": "issuer"
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema))(isUnit)
    },
    test("should fail for attrName not unique") {
      val jsonSchema =
        """
          |{
          |  "name": "Anoncred",
          |  "version": "1.0",
          |  "attrNames": ["attr1", "attr1"],
          |  "issuerId": "issuer"
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq("$.attrNames: the items in the array must be unique")
        )
      )
    },
    test("should fail for not having least 1 characters long") {
      val jsonSchema =
        """
          |{
          |  "name": "",
          |  "version": "",
          |  "attrNames": [""],
          |  "issuerId": ""
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq(
            "$.name: must be at least 1 characters long",
            "$.version: must be at least 1 characters long",
            "$.attrNames[0]: must be at least 1 characters long",
            "$.issuerId: must be at least 1 characters long"
          )
        )
      )
    },
    test("should fail for having null value") {
      val jsonSchema =
        """
          |{
          |  "name": null,
          |  "version": null,
          |  "attrNames": [null],
          |  "issuerId": null
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq(
            "$.name: null found, string expected",
            "$.version: null found, string expected",
            "$.attrNames[0]: null found, string expected",
            "$.issuerId: null found, string expected"
          )
        )
      )
    },
    test("should fail for incorrect type") {
      val jsonSchema =
        """
          |{
          |  "name": 1,
          |  "version": 1,
          |  "attrNames": [1],
          |  "issuerId": 1
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq(
            "$.name: integer found, string expected",
            "$.version: integer found, string expected",
            "$.attrNames[0]: integer found, string expected",
            "$.issuerId: integer found, string expected"
          )
        )
      )
    },
    test("should fail for not having at least 1 attribute") {
      val jsonSchema =
        """
          |{
          |  "name": "Anoncred",
          |  "version": "1.0",
          |  "attrNames": [],
          |  "issuerId": "issuer"
          |}
          |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq("$.attrNames: expected at least 1 items but found 0")
        )
      )
    },
    test("should fail for having more than 125 attributes") {
      val jsonSchema =
        s"""
           |{
           |  "name": "Anoncred",
           |  "version": "1.0",
           |  "attrNames": [${Seq.fill(126)(s""""${Random.alphanumeric.take(10).mkString}"""").mkString(",")}],
           |  "issuerId": "issuer"
           |}
           |""".stripMargin

      val schema: Json = jsonSchema.fromJson[Json].getOrElse(Json.Null)
      assertZIO(AnoncredSchemaType.validate(schema).exit)(
        failsWithErrors(
          Seq("$.attrNames: must have a maximum of 125 items in the array")
        )
      )
    }
  )

  def failsWithErrors(errorMessages: Iterable[String]) = {
    fails(
      isSubtype[JsonSchemaError.JsonValidationErrors](
        hasField("errors", _.errors, hasSameElementsDistinct(errorMessages))
      )
    )
  }
}
