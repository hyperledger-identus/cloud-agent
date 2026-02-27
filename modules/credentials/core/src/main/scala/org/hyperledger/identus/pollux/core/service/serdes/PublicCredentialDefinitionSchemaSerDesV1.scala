package org.hyperledger.identus.pollux.core.service.serdes

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class PublicCredentialPrimaryPublicKeyV1(n: String, s: String, r: Map[String, String], rctxt: String, z: String)

case class PublicCredentialRevocationKeyV1(
    g: String,
    g_dash: String,
    h: String,
    h0: String,
    h1: String,
    h2: String,
    htilde: String,
    h_cap: String,
    u: String,
    pk: String,
    y: String
)

case class PublicCredentialValueV1(
    primary: PublicCredentialPrimaryPublicKeyV1,
    revocation: Option[PublicCredentialRevocationKeyV1]
)

case class PublicCredentialDefinitionSerDesV1(
    schemaId: String,
    `type`: String,
    tag: String,
    value: PublicCredentialValueV1
)

object PublicCredentialDefinitionSerDesV1 {
  val version: String = "PublicCredentialDefinitionV1"

  private val schema: String =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-07/schema#",
      |  "title": "Generated schema for Root",
      |  "type": "object",
      |  "properties": {
      |    "schemaId": {
      |      "type": "string"
      |    },
      |    "type": {
      |      "type": "string"
      |    },
      |    "tag": {
      |      "type": "string"
      |    },
      |    "value": {
      |      "type": "object",
      |      "properties": {
      |        "primary": {
      |          "type": "object",
      |          "properties": {
      |            "n": {
      |              "type": "string"
      |            },
      |            "s": {
      |              "type": "string"
      |            },
      |            "r": {
      |              "type": "object",
      |              "properties": {
      |              }
      |            },
      |            "rctxt": {
      |              "type": "string"
      |            },
      |            "z": {
      |              "type": "string"
      |            }
      |          },
      |          "required": [
      |            "n",
      |            "s",
      |            "r",
      |            "rctxt",
      |            "z"
      |          ]
      |        },
      |        "revocation": {
      |          "type": "object",
      |          "properties": {
      |            "g": {
      |              "type": "string"
      |            },
      |            "g_dash": {
      |              "type": "string"
      |            },
      |            "h": {
      |              "type": "string"
      |            },
      |            "h0": {
      |              "type": "string"
      |            },
      |            "h1": {
      |              "type": "string"
      |            },
      |            "h2": {
      |              "type": "string"
      |            },
      |            "htilde": {
      |              "type": "string"
      |            },
      |            "h_cap": {
      |              "type": "string"
      |            },
      |            "u": {
      |              "type": "string"
      |            },
      |            "pk": {
      |              "type": "string"
      |            },
      |            "y": {
      |              "type": "string"
      |            }
      |          },
      |          "required": [
      |            "g",
      |            "g_dash",
      |            "h",
      |            "h0",
      |            "h1",
      |            "h2",
      |            "htilde",
      |            "h_cap",
      |            "u",
      |            "pk",
      |            "y"
      |          ]
      |        }
      |      },
      |      "required": [
      |        "primary"
      |      ]
      |    },
      |    "issuerId": {
      |      "type": "string"
      |    }
      |  },
      |  "required": [
      |    "schemaId",
      |    "type",
      |    "tag",
      |    "value",
      |    "issuerId"
      |  ]
      |}
      |
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[PublicCredentialDefinitionSerDesV1] = SchemaSerDes(schema)

  given JsonDecoder[PublicCredentialPrimaryPublicKeyV1] = DeriveJsonDecoder.gen[PublicCredentialPrimaryPublicKeyV1]

  given JsonDecoder[PublicCredentialRevocationKeyV1] = DeriveJsonDecoder.gen[PublicCredentialRevocationKeyV1]

  given JsonDecoder[PublicCredentialValueV1] = DeriveJsonDecoder.gen[PublicCredentialValueV1]

  given JsonDecoder[PublicCredentialDefinitionSerDesV1] = DeriveJsonDecoder.gen[PublicCredentialDefinitionSerDesV1]

  given JsonEncoder[PublicCredentialPrimaryPublicKeyV1] = DeriveJsonEncoder.gen[PublicCredentialPrimaryPublicKeyV1]

  given JsonEncoder[PublicCredentialRevocationKeyV1] = DeriveJsonEncoder.gen[PublicCredentialRevocationKeyV1]

  given JsonEncoder[PublicCredentialValueV1] = DeriveJsonEncoder.gen[PublicCredentialValueV1]

  given JsonEncoder[PublicCredentialDefinitionSerDesV1] = DeriveJsonEncoder.gen[PublicCredentialDefinitionSerDesV1]

}
