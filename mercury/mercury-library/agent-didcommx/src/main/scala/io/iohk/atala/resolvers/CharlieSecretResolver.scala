package io.iohk.atala.resolvers

import org.didcommx.didcomm.common.{VerificationMaterial, VerificationMaterialFormat, VerificationMethodType}
import org.didcommx.didcomm.secret.{Secret, SecretResolverInMemory}
import scala.jdk.CollectionConverters._
import org.didcommx.peerdid._

object CharlieSecretResolver {

  private val jwkKey1 =
    // "kid":"${io.iohk.atala.mercury.charlie}#6LSiseNCbbtmG6ascxpPvoyT8ewrWdtJZxwmPNxYAPWxzM8",
    s"""{"kty":"OKP","d":"r-jK2cO3taR8LQnJB1_ikLBTAnOtShJOsHXRUWT-aZA","crv":"X25519","x":"avH0O2Y4tqLAq8y9zpianr8ajii5m4F_mICrzNlatXs"}"""

  private val jwkKey2 = // example from did:example:alice#key-1
    // "kid":"${io.iohk.atala.mercury.charlie}#6MkgLBGee6xL5KH8SZmqmKmQKS2o1qd4RG4dSmjtRGTfsxX",
    s"""{"kty":"OKP","d":"pFRUKkyzx4kHdJtFSnlPA9WzqkDT1HWV0xZ5OYZd2SY","crv":"Ed25519","x":"G-boxFB6vOZBu-wXkm-9Lh79I8nf9Z50cILaOgKKGww"}"""

  private val secretKey1 = new Secret(
    io.iohk.atala.mercury.charlie + "#6LSiseNCbbtmG6ascxpPvoyT8ewrWdtJZxwmPNxYAPWxzM8",
    VerificationMethodType.JSON_WEB_KEY_2020,
    new VerificationMaterial(VerificationMaterialFormat.JWK, jwkKey1)
  )
  private val secretKeyAgreement1 = new Secret(
    io.iohk.atala.mercury.charlie + "#6MkgLBGee6xL5KH8SZmqmKmQKS2o1qd4RG4dSmjtRGTfsxX",
    VerificationMethodType.JSON_WEB_KEY_2020,
    new VerificationMaterial(VerificationMaterialFormat.JWK, jwkKey2)
  )

  val secretResolver = new SecretResolverInMemory(
    Map(
      (io.iohk.atala.mercury.charlie + "#6LSiseNCbbtmG6ascxpPvoyT8ewrWdtJZxwmPNxYAPWxzM8") -> secretKey1,
      (io.iohk.atala.mercury.charlie + "#6MkgLBGee6xL5KH8SZmqmKmQKS2o1qd4RG4dSmjtRGTfsxX") -> secretKeyAgreement1,
      (io.iohk.atala.mercury.charlie) -> secretKeyAgreement1
    ).asJava
  )

  // val service =
  //   """[{
  //     |  "type": "DIDCommMessaging",
  //     |  "serviceEndpoint": "http://localhost:8000/",
  //     |  "routingKeys": ["did:example:somemediator#somekey"]
  //     |},
  //     |{
  //     |  "type": "example",
  //     |  "serviceEndpoint": "http://localhost:8000/",
  //     |  "routingKeys": ["did:example:somemediator#somekey2"],
  //     |  "accept": ["didcomm/v2", "didcomm/aip2;env=rfc587"]
  //     |}]""".stripMargin

  @main def testPeerDidDoc() = {
    println(
      org.didcommx.peerdid.PeerDIDResolver
        .resolvePeerDID(
          io.iohk.atala.mercury.charlie,
          VerificationMaterialFormatPeerDID.JWK
        )
    )
  }

}
