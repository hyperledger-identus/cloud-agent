package org.hyperledger.identus.pollux.core.service.serdes

import zio.*
import zio.test.*
import zio.test.Assertion.*

object AnoncredPresentationRequestSpec extends ZIOSpecDefault {
  val json: String =
    """
      |{
      |  "requested_attributes": {
      |    "attribute1": {
      |      "name": "Attribute 1",
      |      "restrictions": [
      |        {
      |          "cred_def_id": "credential_definition_id_of_attribute1"
      |        }
      |      ],
      |      "non_revoked": {
      |         "from": 1635734400,
      |         "to": 1735734400
      |       }
      |    }
      |  },
      |  "requested_predicates": {
      |    "predicate1": {
      |      "name": "Predicate 1",
      |      "p_type": ">=",
      |      "p_value": 18,
      |      "restrictions": [
      |        {
      |          "schema_id": "schema_id_of_predicate1"
      |        }
      |      ],
      |      "non_revoked": {
      |        "from": 1635734400
      |       }
      |    }
      |  },
      |  "name": "Example Presentation Request",
      |  "nonce": "1234567890",
      |  "version": "1.0"
      |}
      |""".stripMargin

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("AnoncredPresentationRequestSerDes")(
    test("should validate a correct schema") {
      assertZIO(AnoncredPresentationRequestV1.schemaSerDes.validate(json))(isUnit)
    },
    test("should deserialize correctly") {
      val expectedPresentationRequest =
        AnoncredPresentationRequestV1(
          requested_attributes = Map(
            "attribute1" -> AnoncredRequestedAttributeV1(
              "Attribute 1",
              List(
                Map(
                  "cred_def_id" -> "credential_definition_id_of_attribute1"
                )
              ),
              Some(
                AnoncredNonRevokedIntervalV1(
                  Some(1635734400),
                  Some(1735734400)
                )
              )
            )
          ),
          requested_predicates = Map(
            "predicate1" ->
              AnoncredRequestedPredicateV1(
                "Predicate 1",
                ">=",
                18,
                List(
                  Map(
                    "schema_id" -> "schema_id_of_predicate1"
                  )
                ),
                Some(
                  AnoncredNonRevokedIntervalV1(
                    Some(1635734400),
                    None
                  )
                )
              )
          ),
          name = "Example Presentation Request",
          nonce = "1234567890",
          version = "1.0",
          non_revoked = None
        )

      assertZIO(AnoncredPresentationRequestV1.schemaSerDes.deserialize(json))(
        Assertion.equalTo(expectedPresentationRequest)
      )
    }
  )
}
