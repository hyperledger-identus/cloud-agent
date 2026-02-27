package org.hyperledger.identus.pollux.core.service.serdes

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class CredentialDefinitionPrimaryPrivateKeyV1(p: String, q: String)

case class CredentialDefinitionRevocationPrivateKeyV1(x: String, sk: String)

case class PrivateCredentialDefinitionValueV1(
    p_key: CredentialDefinitionPrimaryPrivateKeyV1,
    r_key: Option[CredentialDefinitionRevocationPrivateKeyV1]
)

case class PrivateCredentialDefinitionSchemaSerDesV1(value: PrivateCredentialDefinitionValueV1)

object PrivateCredentialDefinitionSchemaSerDesV1 {
  val version: String = "PrivateCredentialDefinitionV1"
  private val schema: String =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-07/schema#",
      |  "type": "object",
      |  "properties": {
      |    "value": {
      |      "type": "object",
      |      "properties": {
      |        "p_key": {
      |          "type": "object",
      |          "properties": {
      |            "p": { "type": "string" },
      |            "q": { "type": "string" }
      |          },
      |          "required": ["p", "q"]
      |        },
      |        "r_key": {
      |          "type": ["object", "null"],
      |          "properties": {
      |            "x": { "type": "string" },
      |            "sk": { "type": "string" }
      |          },
      |          "required": ["x", "sk"]
      |        }
      |      },
      |      "required": ["p_key"]
      |    }
      |  },
      |  "required": ["value"]
      |}
      |
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[PrivateCredentialDefinitionSchemaSerDesV1] = SchemaSerDes(schema)

  given JsonEncoder[CredentialDefinitionPrimaryPrivateKeyV1] =
    DeriveJsonEncoder.gen[CredentialDefinitionPrimaryPrivateKeyV1]

  given JsonDecoder[CredentialDefinitionPrimaryPrivateKeyV1] =
    DeriveJsonDecoder.gen[CredentialDefinitionPrimaryPrivateKeyV1]

  given JsonEncoder[CredentialDefinitionRevocationPrivateKeyV1] =
    DeriveJsonEncoder.gen[CredentialDefinitionRevocationPrivateKeyV1]

  given JsonDecoder[CredentialDefinitionRevocationPrivateKeyV1] =
    DeriveJsonDecoder.gen[CredentialDefinitionRevocationPrivateKeyV1]

  given JsonEncoder[PrivateCredentialDefinitionValueV1] = DeriveJsonEncoder.gen[PrivateCredentialDefinitionValueV1]

  given JsonDecoder[PrivateCredentialDefinitionValueV1] = DeriveJsonDecoder.gen[PrivateCredentialDefinitionValueV1]

  given JsonEncoder[PrivateCredentialDefinitionSchemaSerDesV1] =
    DeriveJsonEncoder.gen[PrivateCredentialDefinitionSchemaSerDesV1]

  given JsonDecoder[PrivateCredentialDefinitionSchemaSerDesV1] =
    DeriveJsonDecoder.gen[PrivateCredentialDefinitionSchemaSerDesV1]
}
