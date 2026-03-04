package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*

class EdDsaCredentialSigner(keyPair: Ed25519KeyPair) extends CredentialSigner:

  override def algorithm: SignatureAlgorithm = SignatureAlgorithm.EdDSA

  override def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]] =
    ZIO.attempt(keyPair.privateKey.sign(payload))

  override def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean] =
    ZIO.fromTry(
      Apollo.default.ed25519
        .publicKeyFromEncoded(publicKeyBytes)
        .map(pk => pk.verify(payload, signature).isSuccess)
    )
