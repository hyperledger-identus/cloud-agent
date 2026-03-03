package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.crypto.Ed25519PrivateKey
import zio.*

/** Stub SDJwtService for tests that don't exercise actual SD-JWT operations. */
class SDJwtServiceStub extends SDJwtService {
  override def issueCredential(issuerKey: Ed25519PrivateKey, claims: String): CredentialCompact =
    throw new UnsupportedOperationException("SDJwtServiceStub.issueCredential not implemented")

  override def issueCredential(issuerKey: Ed25519PrivateKey, claims: String, holderJwk: String): CredentialCompact =
    throw new UnsupportedOperationException("SDJwtServiceStub.issueCredential not implemented")

  override def createPresentation(sdjwt: CredentialCompact, claimsToDisclose: String): PresentationCompact =
    throw new UnsupportedOperationException("SDJwtServiceStub.createPresentation not implemented")

  override def createPresentation(
      sdjwt: CredentialCompact,
      claimsToDisclose: String,
      nonce: String,
      aud: String,
      holderKey: Ed25519PrivateKey,
  ): PresentationCompact =
    throw new UnsupportedOperationException("SDJwtServiceStub.createPresentation not implemented")
}

object SDJwtServiceStub {
  val layer: ULayer[SDJwtService] = ZLayer.succeed(SDJwtServiceStub())
}
