package org.hyperledger.identus.pollux.core.model.schema.`type`.anoncred

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class AnoncredSchemaSerDesV1(
    name: String,
    version: String,
    attrNames: Set[String],
    issuerId: String
)

object AnoncredSchemaSerDesV1 {
  val version: String = "AnoncredSchemaV1"
  private val schema: String =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-07/schema#",
      |  "type": "object",
      |  "properties": {
      |    "name": {
      |      "type": "string",
      |       "minLength": 1
      |    },
      |    "version": {
      |      "type": "string",
      |       "minLength": 1
      |    },
      |    "attrNames": {
      |      "type": "array",
      |      "items": {
      |        "type": "string",
      |         "minLength": 1
      |      },
      |      "minItems": 1,
      |      "maxItems": 125,
      |      "uniqueItems": true
      |    },
      |    "issuerId": {
      |      "type": "string",
      |      "minLength": 1
      |    }
      |  },
      |  "required": ["name", "version", "attrNames", "issuerId"]
      |}
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[AnoncredSchemaSerDesV1] = SchemaSerDes(schema)

  given JsonEncoder[AnoncredSchemaSerDesV1] = DeriveJsonEncoder.gen[AnoncredSchemaSerDesV1]

  given JsonDecoder[AnoncredSchemaSerDesV1] = DeriveJsonDecoder.gen[AnoncredSchemaSerDesV1]
}
