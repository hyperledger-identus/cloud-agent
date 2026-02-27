package org.hyperledger.identus.pollux.core.service.serdes

import zio.*
import zio.test.*
import zio.test.Assertion.*

object PublicCredentialDefinitionSchemaSerDesSpec extends ZIOSpecDefault {
  val json: String =
    """
      |{
      |  "schemaId": "resource:///anoncred-schema-example.json",
      |  "type": "CL",
      |  "tag": "test",
      |  "value": {
      |    "primary": {
      |      "n": "12873673",
      |      "s": "195958",
      |      "r": {
      |        "dateofissuance": "6594197625",
      |        "drivingclass": "3074617132",
      |        "emailaddress": "3341067570",
      |        "drivinglicenseid": "876491794",
      |        "familyname": "9856376884",
      |        "master_secret": "6838477224"
      |      },
      |      "rctxt": "17824235801",
      |      "z": "91542827065"
      |    },
      |    "revocation": {
      |      "g": "1 16937B88A8",
      |      "g_dash": "1 0850513BB1 ",
      |      "h": "1 16CC8058A8",
      |      "h0": "1 09C6F7A8A8",
      |      "h1": "1 1D3302A8A8",
      |      "h2": "1 222D0DA8A8",
      |      "htilde": "1 187D07A8A8",
      |      "h_cap": "1 2006E7FE67 1 10000",
      |      "u": "1 10B512B541 1 03621",
      |      "pk": "1 13CD12A8A8",
      |      "y": "1 1544B63833 1 0B370"
      |    }
      |  },
      |  "issuerId": "did:prism:557a4ef2ed0cf86fb50d91577269136b3763722ef00a72a1fb1e66895f52b6d8"
      |}
      |""".stripMargin

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("PublicCredentialDefinitionSerDes")(
    test("should validate a correct schema") {
      assertZIO(PublicCredentialDefinitionSerDesV1.schemaSerDes.validate(json))(isUnit)
    },
    test("should deserialise") {
      val primary = PublicCredentialPrimaryPublicKeyV1(
        n = "12873673",
        s = "195958",
        r = Map(
          "dateofissuance" -> "6594197625",
          "drivingclass" -> "3074617132",
          "emailaddress" -> "3341067570",
          "drivinglicenseid" -> "876491794",
          "familyname" -> "9856376884",
          "master_secret" -> "6838477224"
        ),
        rctxt = "17824235801",
        z = "91542827065"
      )

      val revocation = PublicCredentialRevocationKeyV1(
        g = "1 16937B88A8",
        g_dash = "1 0850513BB1 ",
        h = "1 16CC8058A8",
        h0 = "1 09C6F7A8A8",
        h1 = "1 1D3302A8A8",
        h2 = "1 222D0DA8A8",
        htilde = "1 187D07A8A8",
        h_cap = "1 2006E7FE67 1 10000",
        u = "1 10B512B541 1 03621",
        pk = "1 13CD12A8A8",
        y = "1 1544B63833 1 0B370"
      )

      val publicCredentialValue = PublicCredentialValueV1(
        primary = primary,
        revocation = Some(revocation)
      )

      val publicCredentialDefinitionSerDes = PublicCredentialDefinitionSerDesV1(
        schemaId = "resource:///anoncred-schema-example.json",
        `type` = "CL",
        tag = "test",
        value = publicCredentialValue
      )
      assertZIO(PublicCredentialDefinitionSerDesV1.schemaSerDes.deserialize(json))(
        Assertion.equalTo(publicCredentialDefinitionSerDes)
      )
    }
  )
}
