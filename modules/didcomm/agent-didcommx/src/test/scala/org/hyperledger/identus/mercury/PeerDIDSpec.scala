package org.hyperledger.identus.mercury

import com.nimbusds.jose.jwk.OctetKeyPair
import munit.*
import org.hyperledger.identus.mercury.model.DidId

class PeerDIDSpec extends ZSuite {

  test("Make and parse PeerDID") {
    val peer =
      org.hyperledger.identus.mercury.PeerDID.makePeerDid(serviceEndpoint = Some("http://localhost:8654/myendpoint"))

    val did = peer.did.value
    // Exemple {"kty":"OKP","d":"XwaryH2em2iRwqPjxInIHrhvKJqLZ_iejheA5cVM2ZY","crv":"X25519","x":"w1tZHpAuQ6TD6q5cMGWu6q2K1eL3gvEbE1gwkMzLnQI"}
    val agreementKeyJson = peer.jwkForKeyAgreement.toString()
    // Exemple {"kty":"OKP","d":"JdyRa338RKLSO_R9BzCYQnz2o6OAtyf5QwLttSGw3Cs","crv":"Ed25519","x":"m2dKfZnqEsUIisHnpwKGv_yzLWu30vih6Qq_j8Lx5C0"}
    val authenticationKeyJson = peer.jwkForKeyAuthentication.toString()

    val parsedPeerDID = PeerDID(
      did = DidId(did),
      jwkForKeyAgreement = OctetKeyPair.parse(agreementKeyJson),
      jwkForKeyAuthentication = OctetKeyPair.parse(authenticationKeyJson),
    )

    assertEquals(parsedPeerDID, peer)
  }
}
