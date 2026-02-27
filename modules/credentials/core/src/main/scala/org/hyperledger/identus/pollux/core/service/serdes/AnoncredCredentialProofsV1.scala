package org.hyperledger.identus.pollux.core.service.serdes

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class AnoncredCredentialProofV1(
    credential: String,
    requestedAttribute: Seq[String],
    requestedPredicate: Seq[String]
)

case class AnoncredCredentialProofsV1(credentialProofs: List[AnoncredCredentialProofV1])

object AnoncredCredentialProofsV1 {
  val version: String = "AnoncredCredentialProofsV1"
  private val schema: String =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-07/schema#",
      |  "type": "object",
      |  "properties": {
      |    "credentialProofs": {
      |      "type": "array",
      |      "items": {
      |        "type": "object",
      |        "properties": {
      |          "credential": {
      |            "type": "string"
      |          },
      |          "requestedAttribute": {
      |            "type": "array",
      |            "items": {
      |              "type": "string"
      |            }
      |          },
      |          "requestedPredicate": {
      |            "type": "array",
      |            "items": {
      |              "type": "string"
      |            }
      |          }
      |        },
      |        "required": ["credential", "requestedAttribute", "requestedPredicate"]
      |      }
      |    }
      |  },
      |  "required": ["credentialProofs"]
      |}
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[AnoncredCredentialProofsV1] = SchemaSerDes(schema)

  given JsonDecoder[AnoncredCredentialProofV1] =
    DeriveJsonDecoder.gen[AnoncredCredentialProofV1]

  given JsonEncoder[AnoncredCredentialProofV1] =
    DeriveJsonEncoder.gen[AnoncredCredentialProofV1]

  given JsonDecoder[AnoncredCredentialProofsV1] =
    DeriveJsonDecoder.gen[AnoncredCredentialProofsV1]

  given JsonEncoder[AnoncredCredentialProofsV1] =
    DeriveJsonEncoder.gen[AnoncredCredentialProofsV1]
}
