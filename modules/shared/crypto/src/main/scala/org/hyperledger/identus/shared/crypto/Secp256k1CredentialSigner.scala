package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*

class Secp256k1CredentialSigner(keyPair: Secp256k1KeyPair) extends CredentialSigner:

  override def algorithm: SignatureAlgorithm = SignatureAlgorithm.ES256K

  override def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]] =
    ZIO.attempt(keyPair.privateKey.sign(payload))

  override def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean] =
    ZIO.fromTry(
      Apollo.default.secp256k1
        .publicKeyFromEncoded(publicKeyBytes)
        .map(pk => pk.verify(payload, signature).isSuccess)
    )
