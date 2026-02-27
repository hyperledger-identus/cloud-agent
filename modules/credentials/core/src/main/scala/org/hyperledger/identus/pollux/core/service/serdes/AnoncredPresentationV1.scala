package org.hyperledger.identus.pollux.core.service.serdes

import org.hyperledger.identus.shared.json.SchemaSerDes
import zio.*
import zio.json.*

case class AnoncredAdditionalPropertiesTypeStringV1(value: String)

case class AnoncredRevealedAttrV1(sub_proof_index: Int, raw: String, encoded: String)

case class AnoncredRequestedProofV1(
    revealed_attrs: Map[String, AnoncredRevealedAttrV1],
    self_attested_attrs: Map[String, String],
    unrevealed_attrs: Map[String, String],
    predicates: Map[String, AnoncredSubProofIndexV1]
)

case class AnoncredSubProofIndexV1(sub_proof_index: Int)

case class AnoncredIdentifierV1(
    schema_id: String,
    cred_def_id: String,
    rev_reg_id: Option[String],
    timestamp: Option[Int]
)

// Adjusted classes for proofs
case class AnoncredEqProofV1(
    revealed_attrs: Map[String, String],
    a_prime: String,
    e: String,
    v: String,
    m: Map[String, String],
    m2: String
)

case class AnoncredPredicateV1(attr_name: String, p_type: String, value: Int)

case class AnoncredGeProofV1(
    u: Map[String, String],
    r: Map[String, String],
    mj: String,
    alpha: String,
    t: Map[String, String],
    predicate: AnoncredPredicateV1
)

case class AnoncredPrimaryProofV1(eq_proof: AnoncredEqProofV1, ge_proofs: List[AnoncredGeProofV1])

case class AnoncredSubProofV1(primary_proof: AnoncredPrimaryProofV1, non_revoc_proof: Option[AnoncredNonRevocProofV1])

case class AnoncredProofV1(proofs: List[AnoncredSubProofV1], aggregated_proof: AnoncredAggregatedProofV1)

case class AnoncredAggregatedProofV1(c_hash: String, c_list: List[List[Int]])

case class AnoncredNonRevocProofXListV1(
    rho: String,
    r: String,
    r_prime: String,
    r_prime_prime: String,
    r_prime_prime_prime: String,
    o: String,
    o_prime: String,
    m: String,
    m_prime: String,
    t: String,
    t_prime: String,
    m2: String,
    s: String,
    c: String
)

case class AnoncredNonRevocProofCListV1(e: String, d: String, a: String, g: String, w: String, s: String, u: String)

case class AnoncredNonRevocProofV1(x_list: AnoncredNonRevocProofXListV1, c_list: AnoncredNonRevocProofCListV1)

case class AnoncredPresentationV1(
    proof: AnoncredProofV1,
    requested_proof: AnoncredRequestedProofV1,
    identifiers: List[AnoncredIdentifierV1]
)

object AnoncredPresentationV1 {
  val version: String = "AnoncredPresentationV1"

  private val schema: String =
    """
      |{
      |   "$schema":"http://json-schema.org/draft-07/schema#",
      |   "type":"object",
      |   "properties":{
      |      "proof":{
      |         "type":"object",
      |         "properties":{
      |            "proofs":{
      |               "type":"array",
      |               "items":{
      |                  "type":"object",
      |                  "properties":{
      |                     "primary_proof":{
      |                        "type":"object",
      |                        "properties":{
      |                           "eq_proof":{
      |                              "type":"object",
      |                              "properties":{
      |                                 "revealed_attrs":{
      |                                    "type":"object",
      |                                    "additionalProperties":{
      |                                       "type":"string"
      |                                    }
      |                                 },
      |                                 "a_prime":{
      |                                    "type":"string"
      |                                 },
      |                                 "e":{
      |                                    "type":"string"
      |                                 },
      |                                 "v":{
      |                                    "type":"string"
      |                                 },
      |                                 "m":{
      |                                    "type":"object",
      |                                    "additionalProperties":{
      |                                       "type":"string"
      |                                    }
      |                                 },
      |                                 "m2":{
      |                                    "type":"string"
      |                                 }
      |                              },
      |                              "required":[
      |                                 "revealed_attrs",
      |                                 "a_prime",
      |                                 "e",
      |                                 "v",
      |                                 "m",
      |                                 "m2"
      |                              ]
      |                           },
      |                           "ge_proofs":{
      |                              "type":"array",
      |                              "items":{
      |                                 "type":"object",
      |                                 "properties":{
      |                                    "u":{
      |                                       "type":"object",
      |                                       "additionalProperties":{
      |                                          "type":"string"
      |                                       }
      |                                    },
      |                                    "r":{
      |                                       "type":"object",
      |                                       "additionalProperties":{
      |                                          "type":"string"
      |                                       }
      |                                    },
      |                                    "mj":{
      |                                       "type":"string"
      |                                    },
      |                                    "alpha":{
      |                                       "type":"string"
      |                                    },
      |                                    "t":{
      |                                       "type":"object",
      |                                       "additionalProperties":{
      |                                          "type":"string"
      |                                       }
      |                                    },
      |                                    "predicate":{
      |                                       "type":"object",
      |                                       "properties":{
      |                                          "attr_name":{
      |                                             "type":"string"
      |                                          },
      |                                          "p_type":{
      |                                             "type":"string"
      |                                          },
      |                                          "value":{
      |                                             "type":"integer"
      |                                          }
      |                                       },
      |                                       "required":[
      |                                          "attr_name",
      |                                          "p_type",
      |                                          "value"
      |                                       ]
      |                                    }
      |                                 },
      |                                 "required":[
      |                                    "u",
      |                                    "r",
      |                                    "mj",
      |                                    "alpha",
      |                                    "t",
      |                                    "predicate"
      |                                 ]
      |                              }
      |                           }
      |                        },
      |                        "required":[
      |                           "eq_proof",
      |                           "ge_proofs"
      |                        ]
      |                     },
      |                     "non_revoc_proof":{
      |                        "oneOf":[
      |                           {
      |                              "type":"object",
      |                              "properties":{
      |                                 "x_list":{
      |                                    "type":"object",
      |                                    "properties":{
      |                                       "rho":{
      |                                          "type":"string"
      |                                       },
      |                                       "r":{
      |                                          "type":"string"
      |                                       },
      |                                       "r_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "r_prime_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "r_prime_prime_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "o":{
      |                                          "type":"string"
      |                                       },
      |                                       "o_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "m":{
      |                                          "type":"string"
      |                                       },
      |                                       "m_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "t":{
      |                                          "type":"string"
      |                                       },
      |                                       "t_prime":{
      |                                          "type":"string"
      |                                       },
      |                                       "m2":{
      |                                          "type":"string"
      |                                       },
      |                                       "s":{
      |                                          "type":"string"
      |                                       },
      |                                       "c":{
      |                                          "type":"string"
      |                                       }
      |                                    },
      |                                    "required":[
      |                                       "rho",
      |                                       "r",
      |                                       "r_prime",
      |                                       "r_prime_prime",
      |                                       "r_prime_prime_prime",
      |                                       "o",
      |                                       "o_prime",
      |                                       "m",
      |                                       "m_prime",
      |                                       "t",
      |                                       "t_prime",
      |                                       "m2",
      |                                       "s",
      |                                       "c"
      |                                    ]
      |                                 },
      |                                 "c_list":{
      |                                    "type":"object",
      |                                    "properties":{
      |                                       "e":{
      |                                          "type":"string"
      |                                       },
      |                                       "d":{
      |                                          "type":"string"
      |                                       },
      |                                       "a":{
      |                                          "type":"string"
      |                                       },
      |                                       "g":{
      |                                          "type":"string"
      |                                       },
      |                                       "w":{
      |                                          "type":"string"
      |                                       },
      |                                       "s":{
      |                                          "type":"string"
      |                                       },
      |                                       "u":{
      |                                          "type":"string"
      |                                       }
      |                                    },
      |                                    "required":[
      |                                       "e",
      |                                       "d",
      |                                       "a",
      |                                       "g",
      |                                       "w",
      |                                       "s",
      |                                       "u"
      |                                    ]
      |                                 }
      |                              },
      |                              "required":[
      |                                 "x_list",
      |                                 "c_list"
      |                              ]
      |                           },
      |                           {
      |                              "type":"null"
      |                           }
      |                        ]
      |                     }
      |                  },
      |                  "required":[
      |                     "primary_proof"
      |                  ]
      |               }
      |            },
      |            "aggregated_proof":{
      |               "type":"object",
      |               "properties":{
      |                  "c_hash":{
      |                     "type":"string"
      |                  },
      |                  "c_list":{
      |                     "type":"array",
      |                     "items":{
      |                        "type":"array",
      |                        "items":{
      |                           "type":"integer"
      |                        }
      |                     }
      |                  }
      |               },
      |               "required":[
      |                  "c_hash",
      |                  "c_list"
      |               ]
      |            }
      |         },
      |         "required":[
      |            "proofs",
      |            "aggregated_proof"
      |         ]
      |      },
      |      "requested_proof":{
      |         "type":"object",
      |         "properties":{
      |            "revealed_attrs":{
      |               "type":"object",
      |               "additionalProperties":{
      |                  "type":"object",
      |                  "properties":{
      |                     "sub_proof_index":{
      |                        "type":"integer"
      |                     },
      |                     "raw":{
      |                        "type":"string"
      |                     },
      |                     "encoded":{
      |                        "type":"string"
      |                     }
      |                  },
      |                  "required":[
      |                     "sub_proof_index",
      |                     "raw",
      |                     "encoded"
      |                  ]
      |               }
      |            },
      |            "self_attested_attrs":{
      |               "type":"object"
      |            },
      |            "unrevealed_attrs":{
      |               "type":"object"
      |            },
      |            "predicates":{
      |               "type":"object",
      |               "additionalProperties":{
      |                  "type":"object",
      |                  "properties":{
      |                     "sub_proof_index":{
      |                        "type":"integer"
      |                     }
      |                  },
      |                  "required":[
      |                     "sub_proof_index"
      |                  ]
      |               }
      |            }
      |         },
      |         "required":[
      |            "revealed_attrs",
      |            "self_attested_attrs",
      |            "unrevealed_attrs",
      |            "predicates"
      |         ]
      |      },
      |      "identifiers":{
      |         "type":"array",
      |         "items":{
      |            "type":"object",
      |            "properties":{
      |               "schema_id":{
      |                  "type":"string"
      |               },
      |               "cred_def_id":{
      |                  "type":"string"
      |               },
      |               "rev_reg_id":{
      |                  "type":[
      |                     "string",
      |                     "null"
      |                  ]
      |               },
      |               "timestamp":{
      |                  "type":[
      |                     "integer",
      |                     "null"
      |                  ]
      |               }
      |            },
      |            "required":[
      |               "schema_id",
      |               "cred_def_id"
      |            ]
      |         }
      |      }
      |   },
      |   "required":[
      |      "proof",
      |      "requested_proof",
      |      "identifiers"
      |   ]
      |}
      |""".stripMargin

  val schemaSerDes: SchemaSerDes[AnoncredPresentationV1] = SchemaSerDes(schema)

  given JsonDecoder[AnoncredAdditionalPropertiesTypeStringV1] =
    DeriveJsonDecoder.gen[AnoncredAdditionalPropertiesTypeStringV1]

  given JsonEncoder[AnoncredAdditionalPropertiesTypeStringV1] =
    DeriveJsonEncoder.gen[AnoncredAdditionalPropertiesTypeStringV1]

  given JsonDecoder[AnoncredEqProofV1] = DeriveJsonDecoder.gen[AnoncredEqProofV1]

  given JsonEncoder[AnoncredEqProofV1] = DeriveJsonEncoder.gen[AnoncredEqProofV1]

  given JsonDecoder[AnoncredPredicateV1] = DeriveJsonDecoder.gen[AnoncredPredicateV1]

  given JsonEncoder[AnoncredPredicateV1] = DeriveJsonEncoder.gen[AnoncredPredicateV1]

  given JsonDecoder[AnoncredGeProofV1] = DeriveJsonDecoder.gen[AnoncredGeProofV1]

  given JsonEncoder[AnoncredGeProofV1] = DeriveJsonEncoder.gen[AnoncredGeProofV1]

  given JsonDecoder[AnoncredNonRevocProofXListV1] = DeriveJsonDecoder.gen[AnoncredNonRevocProofXListV1]

  given JsonEncoder[AnoncredNonRevocProofXListV1] = DeriveJsonEncoder.gen[AnoncredNonRevocProofXListV1]

  given JsonDecoder[AnoncredNonRevocProofCListV1] = DeriveJsonDecoder.gen[AnoncredNonRevocProofCListV1]

  given JsonEncoder[AnoncredNonRevocProofCListV1] = DeriveJsonEncoder.gen[AnoncredNonRevocProofCListV1]

  given JsonDecoder[AnoncredNonRevocProofV1] = DeriveJsonDecoder.gen[AnoncredNonRevocProofV1]

  given JsonEncoder[AnoncredNonRevocProofV1] = DeriveJsonEncoder.gen[AnoncredNonRevocProofV1]

  given JsonDecoder[AnoncredPrimaryProofV1] = DeriveJsonDecoder.gen[AnoncredPrimaryProofV1]

  given JsonEncoder[AnoncredPrimaryProofV1] = DeriveJsonEncoder.gen[AnoncredPrimaryProofV1]

  given JsonDecoder[AnoncredAggregatedProofV1] = DeriveJsonDecoder.gen[AnoncredAggregatedProofV1]

  given JsonEncoder[AnoncredAggregatedProofV1] = DeriveJsonEncoder.gen[AnoncredAggregatedProofV1]

  given JsonDecoder[AnoncredSubProofV1] = DeriveJsonDecoder.gen[AnoncredSubProofV1]

  given JsonEncoder[AnoncredSubProofV1] = DeriveJsonEncoder.gen[AnoncredSubProofV1]

  given JsonDecoder[AnoncredProofV1] = DeriveJsonDecoder.gen[AnoncredProofV1]

  given JsonEncoder[AnoncredProofV1] = DeriveJsonEncoder.gen[AnoncredProofV1]

  given JsonDecoder[AnoncredRevealedAttrV1] = DeriveJsonDecoder.gen[AnoncredRevealedAttrV1]

  given JsonEncoder[AnoncredRevealedAttrV1] = DeriveJsonEncoder.gen[AnoncredRevealedAttrV1]

  given JsonDecoder[AnoncredSubProofIndexV1] = DeriveJsonDecoder.gen[AnoncredSubProofIndexV1]

  given JsonEncoder[AnoncredSubProofIndexV1] = DeriveJsonEncoder.gen[AnoncredSubProofIndexV1]

  given JsonDecoder[AnoncredRequestedProofV1] = DeriveJsonDecoder.gen[AnoncredRequestedProofV1]

  given JsonEncoder[AnoncredRequestedProofV1] = DeriveJsonEncoder.gen[AnoncredRequestedProofV1]

  given JsonDecoder[AnoncredIdentifierV1] = DeriveJsonDecoder.gen[AnoncredIdentifierV1]

  given JsonEncoder[AnoncredIdentifierV1] = DeriveJsonEncoder.gen[AnoncredIdentifierV1]

  given JsonDecoder[AnoncredPresentationV1] = DeriveJsonDecoder.gen[AnoncredPresentationV1]

  given JsonEncoder[AnoncredPresentationV1] = DeriveJsonEncoder.gen[AnoncredPresentationV1]

}
