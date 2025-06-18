package org.hyperledger.identus.pollux.core.service.serdes

import zio.*
import zio.test.*
import zio.test.Assertion.*

object AnoncredPresentationSpec extends ZIOSpecDefault {
  val json: String =
    """
      |{
      |  "proof": {
      |    "proofs": [
      |      {
      |        "primary_proof": {
      |          "eq_proof": {
      |            "revealed_attrs": {
      |              "sex": "4046863..."
      |            },
      |            "a_prime": "2329247...",
      |            "e": "1666946...",
      |            "v": "2137556...",
      |            "m": {
      |              "age": "1025474...",
      |              "master_secret": "8005118...",
      |              "name": "1031839..."
      |            },
      |            "m2": "5852217..."
      |          },
      |          "ge_proofs": [
      |            {
      |              "u": {
      |                "1": "1135835...",
      |                "0": "5209733...",
      |                "3": "6777506...",
      |                "2": "8790500..."
      |              },
      |              "r": {
      |                "DELTA": "1094759...",
      |                "3": "3908998...",
      |                "1": "3551550...",
      |                "2": "4594656...",
      |                "0": "7769940..."
      |              },
      |              "mj": "1025474...",
      |              "alpha": "4089177...",
      |              "t": {
      |                "0": "1208121...",
      |                "DELTA": "9078219...",
      |                "1": "5769003...",
      |                "3": "4089901...",
      |                "2": "2261914..."
      |              },
      |              "predicate": {
      |                "attr_name": "age",
      |                "p_type": "GE",
      |                "value": 18
      |              }
      |            }
      |          ]
      |        },
      |        "non_revoc_proof": null
      |      }
      |    ],
      |    "aggregated_proof": {
      |      "c_hash": "3880251...",
      |      "c_list": [
      |        [184, 131, 15],
      |        [95, 179],
      |        [1, 200, 254],
      |        [179, 45, 156],
      |        [1, 67, 251],
      |        [2, 207, 34, 43]
      |      ]
      |    }
      |  },
      |  "requested_proof": {
      |    "revealed_attrs": {
      |      "sex": {
      |        "sub_proof_index": 0,
      |        "raw": "M",
      |        "encoded": "4046863..."
      |      }
      |    },
      |    "self_attested_attrs":{},
      |    "unrevealed_attrs":{},
      |    "predicates": {
      |      "age": {
      |        "sub_proof_index": 0
      |      }
      |    }
      |  },
      |  "identifiers": [
      |    {
      |      "schema_id": "resource:///anoncred-presentation-schema-example.json",
      |      "cred_def_id": "resource:///anoncred-presentation-credential-definition-example.json",
      |      "rev_reg_id": null,
      |      "timestamp": null
      |    }
      |  ]
      |}
      |
      |""".stripMargin

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("AnoncredPresentationRequestSerDes")(
    test("should validate a correct schema") {
      assertZIO(AnoncredPresentationV1.schemaSerDes.validate(json))(isUnit)
    },
    test("should deserialize correctly") {
      val predicate = AnoncredPredicateV1(
        attr_name = "age",
        p_type = "GE",
        value = 18
      )

      val geProof = AnoncredGeProofV1(
        u = Map(
          "1" -> "1135835...",
          "0" -> "5209733...",
          "3" -> "6777506...",
          "2" -> "8790500..."
        ),
        r = Map(
          "DELTA" -> "1094759...",
          "3" -> "3908998...",
          "1" -> "3551550...",
          "2" -> "4594656...",
          "0" -> "7769940..."
        ),
        mj = "1025474...",
        alpha = "4089177...",
        t = Map(
          "0" -> "1208121...",
          "DELTA" -> "9078219...",
          "1" -> "5769003...",
          "3" -> "4089901...",
          "2" -> "2261914..."
        ),
        predicate = predicate
      )

      val eqProof = AnoncredEqProofV1(
        revealed_attrs = Map("sex" -> "4046863..."),
        a_prime = "2329247...",
        e = "1666946...",
        v = "2137556...",
        m = Map(
          "age" -> "1025474...",
          "master_secret" -> "8005118...",
          "name" -> "1031839..."
        ),
        m2 = "5852217..."
      )

      val primaryProof = AnoncredPrimaryProofV1(
        eq_proof = eqProof,
        ge_proofs = List(geProof)
      )

      val subProof = AnoncredSubProofV1(
        primary_proof = primaryProof,
        non_revoc_proof = None
      )

      val aggregatedProof = AnoncredAggregatedProofV1(
        c_hash = "3880251...",
        c_list = List(
          List(184, 131, 15),
          List(95, 179),
          List(1, 200, 254),
          List(179, 45, 156),
          List(1, 67, 251),
          List(2, 207, 34, 43)
        )
      )

      val revealedAttr = AnoncredRevealedAttrV1(
        sub_proof_index = 0,
        raw = "M",
        encoded = "4046863..."
      )

      val requestedProof = AnoncredRequestedProofV1(
        revealed_attrs = Map("sex" -> revealedAttr),
        self_attested_attrs = Map.empty,
        unrevealed_attrs = Map.empty,
        predicates = Map("age" -> AnoncredSubProofIndexV1(0))
      )

      val identifier = AnoncredIdentifierV1(
        schema_id = "resource:///anoncred-presentation-schema-example.json",
        cred_def_id = "resource:///anoncred-presentation-credential-definition-example.json",
        rev_reg_id = None,
        timestamp = None
      )

      val anoncredPresentationV1 = AnoncredPresentationV1(
        proof = AnoncredProofV1(
          proofs = List(subProof),
          aggregated_proof = aggregatedProof
        ),
        requested_proof = requestedProof,
        identifiers = List(identifier)
      )

      assert(AnoncredPresentationV1.schemaSerDes.serializeToJsonString(anoncredPresentationV1))(Assertion.equalTo(json))

      assertZIO(AnoncredPresentationV1.schemaSerDes.deserialize(json))(
        Assertion.equalTo(anoncredPresentationV1)
      )
    }
  )
}
