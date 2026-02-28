package org.hyperledger.identus.resolvers

import org.didcommx.didcomm.common.{VerificationMaterial, VerificationMaterialFormat, VerificationMethodType}
import org.didcommx.didcomm.diddoc.{DIDCommService, DIDDoc, VerificationMethod}

import scala.jdk.CollectionConverters.*

object AliceDidDoc {
  val did = "did:example:alice"
  val keyAgreements = Seq(s"$did#key-agreement-1").asJava

  val verficationMethods = Seq(
    new VerificationMethod(
      s"$did#key-3",
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(
        VerificationMaterialFormat.JWK,
        """{
          |  "kty":"EC",
          |  "crv":"secp256k1",
          |  "x":"aToW5EaTq5mlAf8C5ECYDSkqsJycrW-e1SQ6_GJcAOk",
          |  "y":"JAGX94caA21WKreXwYUaOCYTBMrqaX4KWIlsQZTHWCk"
          |}""".stripMargin
      ),
      s"$did#key-3"
    ),
    new VerificationMethod(
      s"$did#key-agreement-1",
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(
        VerificationMaterialFormat.JWK,
        """{
          |  "kty":"OKP",
          |  "crv":"X25519",
          |  "x":"avH0O2Y4tqLAq8y9zpianr8ajii5m4F_mICrzNlatXs"
          |}""".stripMargin
      ),
      s"$did#key-agreement-1"
    )
  ).asJava

  val authentications = Seq(s"$did#key-3").asJava

  val didCommServices = Seq.empty[DIDCommService].asJava
  val didDocAlice = new DIDDoc(
    did,
    keyAgreements,
    authentications,
    verficationMethods,
    didCommServices
  )
}
