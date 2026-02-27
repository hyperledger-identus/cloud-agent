package org.hyperledger.identus.pollux.core.service.serdes

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class ProofKeyCredentialDefinitionSchemaSerDesV1(c: String, xz_cap: String, xr_cap: List[List[String]])

object ProofKeyCredentialDefinitionSchemaSerDesV1 {
  val version: String = "ProofKeyCredentialDefinitionV1"
  private val schema: String =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-07/schema#",
      |  "type": "object",
      |  "properties": {
      |    "c": {
      |      "type": "string"
      |    },
      |    "xz_cap": {
      |      "type": "string"
      |    },
      |    "xr_cap": {
      |      "type": "array",
      |      "items": {
      |        "type": "array",
      |        "items": {
      |          "type": "string"
      |        }
      |      }
      |    }
      |  },
      |  "required": [
      |    "c",
      |    "xz_cap",
      |    "xr_cap"
      |  ]
      |}
      |
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[ProofKeyCredentialDefinitionSchemaSerDesV1] = SchemaSerDes(schema)

  given JsonDecoder[ProofKeyCredentialDefinitionSchemaSerDesV1] =
    DeriveJsonDecoder.gen[ProofKeyCredentialDefinitionSchemaSerDesV1]

  given JsonEncoder[ProofKeyCredentialDefinitionSchemaSerDesV1] =
    DeriveJsonEncoder.gen[ProofKeyCredentialDefinitionSchemaSerDesV1]
}
