package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*

object EdDsaCredentialSignerSpec extends ZIOSpecDefault:

  override def spec = suite("EdDsaCredentialSigner")(
    test("algorithm is EdDSA") {
      val keyPair = Apollo.default.ed25519.generateKeyPair
      val signer = EdDsaCredentialSigner(keyPair)
      assertTrue(signer.algorithm == SignatureAlgorithm.EdDSA)
    },
    test("sign and verify round-trip succeeds") {
      val keyPair = Apollo.default.ed25519.generateKeyPair
      val signer = EdDsaCredentialSigner(keyPair)
      val payload = "test payload".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.EdDSA)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(payload, signature, keyPair.publicKey.getEncoded)
      yield assertTrue(valid)
    },
    test("verify fails with wrong public key") {
      val keyPair1 = Apollo.default.ed25519.generateKeyPair
      val keyPair2 = Apollo.default.ed25519.generateKeyPair
      val signer = EdDsaCredentialSigner(keyPair1)
      val payload = "test payload".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.EdDSA)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(payload, signature, keyPair2.publicKey.getEncoded)
      yield assertTrue(!valid)
    },
    test("verify fails with tampered payload") {
      val keyPair = Apollo.default.ed25519.generateKeyPair
      val signer = EdDsaCredentialSigner(keyPair)
      val payload = "original".getBytes("UTF-8")
      val tampered = "tampered".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.EdDSA)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(tampered, signature, keyPair.publicKey.getEncoded)
      yield assertTrue(!valid)
    },
  )
