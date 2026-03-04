package org.hyperledger.identus.shared.credentials

import zio.*

trait CredentialSigner:
  def algorithm: SignatureAlgorithm
  def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]]
  def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean]
