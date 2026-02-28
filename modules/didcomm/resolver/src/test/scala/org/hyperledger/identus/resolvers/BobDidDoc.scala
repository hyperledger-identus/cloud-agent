package org.hyperledger.identus.resolvers

import org.didcommx.didcomm.common.{VerificationMaterial, VerificationMaterialFormat, VerificationMethodType}
import org.didcommx.didcomm.diddoc.{DIDCommService, DIDDoc, VerificationMethod}

import scala.jdk.CollectionConverters.*

object BobDidDoc {
  val did = "did:example:bob"
  val authentications = Seq(s"$did#key-3").asJava
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
          |  "x":"GDTrI66K0pFfO54tlCSvfjjNapIs44dzpneBgyx0S3E"
          |}""".stripMargin
      ),
      s"$did#key-agreement-1"
    ),
    new VerificationMethod(
      s"$did#key-agreement-2",
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(
        VerificationMaterialFormat.JWK,
        """{
          |  "kty":"OKP",
          |  "crv":"X25519",
          |  "x":"UT9S3F5ep16KSNBBShU2wh3qSfqYjlasZimn0mB8_VM"
          |}""".stripMargin
      ),
      s"$did:#key-agreement-2"
    )
  ).asJava
  val didCommServices = Seq(
    new DIDCommService(
      "did:example:mediator#didcomm-1",
      "http://identus.io/path",
      Seq("did:example:mediator#key-agreement-1").asJava,
      Seq("didcomm/v2").asJava
    )
  ).asJava

  val didDocBob = new DIDDoc(
    did,
    keyAgreements,
    authentications,
    verficationMethods,
    didCommServices
  )

}
