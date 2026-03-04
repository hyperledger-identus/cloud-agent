package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*

object Secp256k1CredentialSignerSpec extends ZIOSpecDefault:

  override def spec = suite("Secp256k1CredentialSigner")(
    test("algorithm is ES256K") {
      val keyPair = Apollo.default.secp256k1.generateKeyPair
      val signer = Secp256k1CredentialSigner(keyPair)
      assertTrue(signer.algorithm == SignatureAlgorithm.ES256K)
    },
    test("sign and verify round-trip succeeds") {
      val keyPair = Apollo.default.secp256k1.generateKeyPair
      val signer = Secp256k1CredentialSigner(keyPair)
      val payload = "test payload".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.ES256K)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(payload, signature, keyPair.publicKey.getEncoded)
      yield assertTrue(valid)
    },
    test("verify fails with wrong public key") {
      val keyPair1 = Apollo.default.secp256k1.generateKeyPair
      val keyPair2 = Apollo.default.secp256k1.generateKeyPair
      val signer = Secp256k1CredentialSigner(keyPair1)
      val payload = "test payload".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.ES256K)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(payload, signature, keyPair2.publicKey.getEncoded)
      yield assertTrue(!valid)
    },
    test("verify fails with tampered payload") {
      val keyPair = Apollo.default.secp256k1.generateKeyPair
      val signer = Secp256k1CredentialSigner(keyPair)
      val payload = "original".getBytes("UTF-8")
      val tampered = "tampered".getBytes("UTF-8")
      val keyRef = KeyRef("test-key", SignatureAlgorithm.ES256K)
      for
        signature <- signer.sign(payload, keyRef)
        valid <- signer.verify(tampered, signature, keyPair.publicKey.getEncoded)
      yield assertTrue(!valid)
    },
  )
