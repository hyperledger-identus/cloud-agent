package org.hyperledger.identus.resolvers
import munit.*
import org.didcommx.peerdid.*
import zio.json.ast.Json
import zio.json.DecoderOps

import scala.jdk.CollectionConverters.*

class PeerDidResolverSpec extends ZSuite {

  testZ("peer did") {
    val peerDid =
      "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0"
    val expectedDidDocJson = """{
        |   "id": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0",
        |   "authentication": [
        |       {
        |           "id": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0#key-2",
        |           "type": "Ed25519VerificationKey2020",
        |           "controller": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0",
        |           "publicKeyMultibase": "z6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V"
        |       },
        |       {
        |           "id": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0#key-3",
        |           "type": "Ed25519VerificationKey2020",
        |           "controller": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0",
        |           "publicKeyMultibase": "z6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg"
        |       }
        |   ],
        |   "keyAgreement": [
        |       {
        |           "id": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0#key-1",
        |           "type": "X25519KeyAgreementKey2020",
        |           "controller": "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.Vz6MkgoLTnTypo3tDRwCkZXSccTPHRLhF4ZnjhueYAFpEX6vg.SeyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0",
        |           "publicKeyMultibase": "z6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc"
        |       }
        |   ],
        |   "service": [
        |       {
        |           "id": "#service",
        |           "type": "DIDCommMessaging",
        |           "serviceEndpoint": {
        |             "uri" : "https://example.com/endpoint",
        |             "routingKeys": [
        |               "did:example:somemediator#somekey"
        |             ],
        |             "accept": [
        |                "didcomm/v2", "didcomm/aip2;env=rfc587"
        |             ]
        |           }
        |       }
        |   ]
        |}""".stripMargin.fromJson[Json].toOption

    val peerDidResolver = PeerDidResolverImpl()
    val didDocJson = peerDidResolver.resolveDidAsJson(peerDid)
    didDocJson.map(assertEquals(_, expectedDidDocJson))

    // assertEqualsZ(didDocJson,expectedDidDocJson) // this fails need find why
  }

  val keyAgreement = VerificationMaterialPeerDID[VerificationMethodTypeAgreement](
    VerificationMaterialFormatPeerDID.MULTIBASE,
    "z6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc",
    VerificationMethodTypeAgreement.X25519_KEY_AGREEMENT_KEY_2020.INSTANCE
  )
  val keyAuthentication = VerificationMaterialPeerDID[VerificationMethodTypeAuthentication](
    VerificationMaterialFormatPeerDID.MULTIBASE,
    "z6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V",
    VerificationMethodTypeAuthentication.ED25519_VERIFICATION_KEY_2020.INSTANCE
  )

  val service =
    """[{
      |  "type": "DIDCommMessaging",
      |  "serviceEndpoint": "http://localhost:8000/",
      |  "routingKeys": ["did:example:somemediator#somekey"]
      |},
      |{
      |  "type": "example",
      |  "serviceEndpoint": "http://localhost:8000/",
      |  "routingKeys": ["did:example:somemediator#somekey2"],
      |  "accept": ["didcomm/v2", "didcomm/aip2;env=rfc587"]
      |}]""".stripMargin

  def didPeerExample = org.didcommx.peerdid.PeerDIDCreator.createPeerDIDNumalgo2(
    List(keyAgreement).asJava,
    List(keyAuthentication).asJava,
    service
  )

  test("peer did creation (from the example)") {
    assertEquals(
      didPeerExample,
      "did:peer:2.Ez6LSbysY2xFMRpGMhb7tFTLMpeuPRaqaWM1yECx2AtzE3KCc.Vz6MkqRYqQiSgvZQdnBytw86Qbs2ZWUkGv22od935YF4s8M7V.SW3sidCI6ImRtIiwicyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODAwMC8iLCJyIjpbImRpZDpleGFtcGxlOnNvbWVtZWRpYXRvciNzb21la2V5Il19LHsidCI6ImV4YW1wbGUiLCJzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDAwLyIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkyIl0sImEiOlsiZGlkY29tbS92MiIsImRpZGNvbW0vYWlwMjtlbnY9cmZjNTg3Il19XQ"
    )
  }

  test("get did DIDDoc from the DID peer example") {
    assertEquals(
      didPeerExample,
      PeerDidResolver.getDIDDoc(didPeerExample).getDid()
    )
  }

  val keyAgreement2 = VerificationMaterialPeerDID[VerificationMethodTypeAgreement](
    VerificationMaterialFormatPeerDID.JWK,
    """{"kty":"OKP","crv":"X25519","x":"avH0O2Y4tqLAq8y9zpianr8ajii5m4F_mICrzNlatXs"}""",
    VerificationMethodTypeAgreement.JSON_WEB_KEY_2020.INSTANCE
  )
  val keyAuthentication2 = VerificationMaterialPeerDID[VerificationMethodTypeAuthentication](
    VerificationMaterialFormatPeerDID.JWK,
    """{"kty":"OKP","crv":"Ed25519","x":"G-boxFB6vOZBu-wXkm-9Lh79I8nf9Z50cILaOgKKGww"}""",
    VerificationMethodTypeAuthentication.JSON_WEB_KEY_2020.INSTANCE
  )

  def didPeerExample2 = org.didcommx.peerdid.PeerDIDCreator.createPeerDIDNumalgo2(
    List(keyAgreement2).asJava,
    List(keyAuthentication2).asJava,
    service
  )

  test("TODO") {
    println(didPeerExample2)
    println(PeerDidResolver.getDIDDoc(didPeerExample2))
  }
}
