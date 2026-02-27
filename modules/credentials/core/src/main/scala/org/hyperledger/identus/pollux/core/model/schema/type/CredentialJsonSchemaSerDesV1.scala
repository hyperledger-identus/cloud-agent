package org.hyperledger.identus.pollux.core.model.schema.`type`

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*
import zio.json.ast.Json

case class CredentialJsonSchemaSerDesV1(
    $schema: String,
    $id: Option[String],
    description: Option[String],
    properties: Json,
    required: Option[Set[String]],
    additionalProperties: Option[Boolean]
)

object CredentialJsonSchemaSerDesV1 {
  val version: String = "CredentialSchemaSchemaV1"

  private val schema: String = """
      |{
      |  "$schema": "https://json-schema.org/draft/2020-12/schema",
      |  "$id": "https://example.com/custom-meta-schema",
      |  "type": "object",
      |  "properties": {
      |    "$id": {
      |      "type": "string",
      |      "format": "uri-reference"
      |    },
      |    "$schema": {
      |      "type": "string",
      |      "format": "uri",
      |      "enum": ["https://json-schema.org/draft/2020-12/schema"]
      |    },
      |    "description": {
      |      "type": "string",
      |      "minLength": 1,
      |      "maxLength": 2000
      |    },
      |    "type": {
      |      "enum": ["object"]
      |    },
      |    "properties": {
      |      "type": "object",
      |      "patternProperties": {
      |        ".*": {
      |          "$ref": "#/$defs/oneOfDef"
      |        }
      |      }
      |    },
      |    "required": {
      |      "type": "array",
      |      "items": {
      |        "type": "string",
      |        "minLength": 1
      |      },
      |      "minItems": 0,
      |      "maxItems": 125,
      |      "uniqueItems": true
      |    },
      |    "additionalProperties": {
      |      "type": "boolean"
      |    }
      |  },
      |  "required": ["$schema","type","properties"],
      |  "additionalProperties": false,
      |  "$defs": {
      |    "oneOfDef": {
      |      "oneOf": [
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["string"]
      |            },
      |            "minLength": {
      |              "type": "integer",
      |              "minimum": 0
      |            },
      |            "maxLength": {
      |              "type": "integer",
      |              "minimum": 0
      |            },
      |            "format": {
      |              "type": "string",
      |              "enum": ["date-time","date","time","duration","ipv4","ipv6","email","uri","uuid"]
      |            },
      |            "pattern": {
      |              "type": "string",
      |              "format": "regex"
      |            },
      |            "enum": {
      |              "type": "array",
      |              "items": {
      |                "type": "string",
      |                "minItems": 1
      |              }
      |            }
      |          },
      |          "required": ["type"],
      |          "additionalProperties": false
      |        },
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["integer"]
      |            },
      |            "minimum": {
      |              "type": "integer"
      |            },
      |            "exclusiveMinimum": {
      |              "type": "integer"
      |            },
      |            "maximum": {
      |              "type": "integer"
      |            },
      |            "exclusiveMaximum": {
      |              "type": "integer"
      |            },
      |            "enum": {
      |              "type": "array",
      |              "items": {
      |                "type": "integer",
      |                "minItems": 1
      |              }
      |            }
      |          },
      |          "required": ["type"],
      |          "additionalProperties": false
      |        },
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["number"]
      |            },
      |            "minimum": {
      |              "type": "number"
      |            },
      |            "exclusiveMinimum": {
      |              "type": "number"
      |            },
      |            "maximum": {
      |              "type": "number"
      |            },
      |            "exclusiveMaximum": {
      |              "type": "number"
      |            },
      |            "enum": {
      |              "type": "array",
      |              "items": {
      |                "type": "number",
      |                "minItems": 1
      |              }
      |            }
      |          },
      |          "required": ["type"],
      |          "additionalProperties": false
      |        },
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["boolean"]
      |            }
      |          },
      |          "required": ["type"],
      |          "additionalProperties": false
      |        },
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["array"]
      |            },
      |            "items": {
      |              "$ref": "#/$defs/oneOfDef"
      |            },
      |            "minItems": {
      |              "type": "integer"
      |            },
      |            "maxItems": {
      |              "type": "integer"
      |            },
      |            "uniqueItems": {
      |              "type": "boolean"
      |            }
      |          },
      |          "required": ["type","items"],
      |          "additionalProperties": false
      |        },
      |        {
      |          "type": "object",
      |          "properties": {
      |            "type": {
      |              "type": "string",
      |              "enum": ["object"]
      |            },
      |            "properties": {
      |              "type": "object",
      |              "patternProperties": {
      |                ".*": {
      |                  "$ref": "#/$defs/oneOfDef"
      |                }
      |              }
      |            },
      |            "minProperties": {
      |              "type": "integer"
      |            },
      |            "maxProperties": {
      |              "type": "integer"
      |            }
      |          },
      |          "required": ["type","properties"],
      |          "additionalProperties": false
      |        }
      |      ]
      |    }
      |  }
      |}
      """.stripMargin

  val schemaSerDes: SchemaSerDes[CredentialJsonSchemaSerDesV1] = SchemaSerDes(schema)

  given JsonEncoder[CredentialJsonSchemaSerDesV1] = DeriveJsonEncoder.gen[CredentialJsonSchemaSerDesV1]

  given JsonDecoder[CredentialJsonSchemaSerDesV1] = DeriveJsonDecoder.gen[CredentialJsonSchemaSerDesV1]
}
