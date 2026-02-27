package org.hyperledger.identus.mercury.resolvers

import org.didcommx.didcomm.common.{VerificationMaterial, VerificationMaterialFormat, VerificationMethodType}
import org.didcommx.didcomm.secret.{Secret, SecretResolverInMemory}

import scala.jdk.CollectionConverters.*

object BobSecretResolver {
  val jwkKey1 =
    """{
      |  "kty":"EC",
      |  "d":"N3Hm1LXA210YVGGsXw_GklMwcLu_bMgnzDese6YQIyA",
      |  "crv":"secp256k1",
      |  "x":"aToW5EaTq5mlAf8C5ECYDSkqsJycrW-e1SQ6_GJcAOk",
      |  "y":"JAGX94caA21WKreXwYUaOCYTBMrqaX4KWIlsQZTHWCk"
      |}""".stripMargin
  val jwkKey2 =
    """{
      |  "kty":"OKP",
      |  "d":"b9NnuOCB0hm7YGNvaE9DMhwH_wjZA1-gWD6dA0JWdL0",
      |  "crv":"X25519",
      |  "x":"GDTrI66K0pFfO54tlCSvfjjNapIs44dzpneBgyx0S3E"
      |}""".stripMargin

  val secretKey1 = new Secret(
    "did:example:bob#key-3",
    VerificationMethodType.JSON_WEB_KEY_2020,
    new VerificationMaterial(VerificationMaterialFormat.JWK, jwkKey1)
  )
  val secretKeyAgreement1 = new Secret(
    "did:example:bob#key-agreement-1",
    VerificationMethodType.JSON_WEB_KEY_2020,
    new VerificationMaterial(VerificationMaterialFormat.JWK, jwkKey2)
  )

  val secretResolver = new SecretResolverInMemory(
    Map("did:example:bob#key-3" -> secretKey1, "did:example:bob#key-agreement-1" -> secretKeyAgreement1).asJava
  )

}
