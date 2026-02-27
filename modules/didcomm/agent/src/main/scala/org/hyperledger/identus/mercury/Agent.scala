package org.hyperledger.identus.mercury

import org.hyperledger.identus.mercury.model.DidId

enum Agent(val id: DidId):
  case Alice extends Agent(DidId("did:example:alice"))
  case Bob extends Agent(DidId("did:example:bob"))
  case Mediator extends Agent(DidId("did:example:mediator"))
  // case Charlie extends Agent(DidId(charlie))
  case Charlie2
      extends Agent(DidId({
        val S =
          // "eyJ0IjoiZG0iLCJzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDAwIiwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0"
          // "eyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0="
          // "eyJ0IjoiZG0iLCJzIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9lbmRwb2ludCIsInIiOlsiZGlkOmV4YW1wbGU6c29tZW1lZGlhdG9yI3NvbWVrZXkiXSwiYSI6WyJkaWRjb21tL3YyIiwiZGlkY29tbS9haXAyO2Vudj1yZmM1ODciXX0"
          "eyJpZCI6Im5ldy1pZCIsInQiOiJkbSIsInMiOiJodHRwOi8vcm9vdHNpZC1tZWRpYXRvcjo4MDAwIiwiYSI6WyJkaWRjb21tL3YyIl19"

        "did:peer:2.Ez6LSiseNCbbtmG6ascxpPvoyT8ewrWdtJZxwmPNxYAPWxzM8" +
          // ".Ez" + "6LSdJfkX7F3BJsYUwjDNeptqV4Wb9md6YWd3gCewQBycmwE" +
          ".Vz" + "6MkgLBGee6xL5KH8SZmqmKmQKS2o1qd4RG4dSmjtRGTfsxX" +
          ".S" + S
      }))

// import org.didcommx.peerdid._
// import scala.jdk.CollectionConverters._

// val keyAgreement = VerificationMaterialPeerDID[VerificationMethodTypeAgreement](
//   VerificationMaterialFormatPeerDID.JWK,
//   """{"kty":"OKP","crv":"X25519","x":"avH0O2Y4tqLAq8y9zpianr8ajii5m4F_mICrzNlatXs"}""",
//   VerificationMethodTypeAgreement.JSON_WEB_KEY_2020.INSTANCE
// )
// val keyAgreement2 = VerificationMaterialPeerDID[VerificationMethodTypeAgreement](
//   VerificationMaterialFormatPeerDID.JWK,
//   """{"kty":"EC","crv":"P-256","x":"2syLh57B-dGpa0F8p1JrO6JU7UUSF6j7qL-vfk1eOoY","y":"BgsGtI7UPsObMRjdElxLOrgAO9JggNMjOcfzEPox18w"}""",
//   VerificationMethodTypeAgreement.JSON_WEB_KEY_2020.INSTANCE
// )
// val keyAuthentication = VerificationMaterialPeerDID[VerificationMethodTypeAuthentication](
//   VerificationMaterialFormatPeerDID.JWK,
//   """{"kty":"OKP","crv":"Ed25519","x":"G-boxFB6vOZBu-wXkm-9Lh79I8nf9Z50cILaOgKKGww"}""",
//   VerificationMethodTypeAuthentication.JSON_WEB_KEY_2020.INSTANCE
// )
// val service =
//   """[{
//       |  "type": "DIDCommMessaging",
//       |  "serviceEndpoint": "http://localhost:8000/",
//       |  "routingKeys": ["did:example:somemediator#somekey"]
//       |},
//       |{
//       |  "type": "DIDCommMessaging",
//       |  "serviceEndpoint": "http://localhost:8000/",
//       |  "routingKeys": ["did:example:somemediator#somekey2"],
//       |  "accept": ["didcomm/v2", "didcomm/aip2;env=rfc587"]
//       |}]""".stripMargin

// def charlie = org.didcommx.peerdid.PeerDIDCreator.createPeerDIDNumalgo2(
//   List(keyAgreement).asJava,
//   List(keyAuthentication).asJava,
//   null, // service
// )
