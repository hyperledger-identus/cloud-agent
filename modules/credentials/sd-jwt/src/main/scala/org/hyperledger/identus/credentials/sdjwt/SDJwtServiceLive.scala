package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.crypto.Ed25519PrivateKey
import zio.*

class SDJwtServiceLive extends SDJwtService {

  override def issueCredential(issuerKey: Ed25519PrivateKey, claims: String): CredentialCompact =
    SDJWT.issueCredential(IssuerPrivateKey(issuerKey), claims)

  override def issueCredential(
      issuerKey: Ed25519PrivateKey,
      claims: String,
      holderJwk: String,
  ): CredentialCompact =
    SDJWT.issueCredential(IssuerPrivateKey(issuerKey), claims, HolderPublicKey.fromJWT(holderJwk))

  override def createPresentation(sdjwt: CredentialCompact, claimsToDisclose: String): PresentationCompact =
    SDJWT.createPresentation(sdjwt, claimsToDisclose)

  override def createPresentation(
      sdjwt: CredentialCompact,
      claimsToDisclose: String,
      nonce: String,
      aud: String,
      holderKey: Ed25519PrivateKey,
  ): PresentationCompact =
    SDJWT.createPresentation(sdjwt, claimsToDisclose, nonce, aud, HolderPrivateKey(holderKey))
}

object SDJwtServiceLive {
  val layer: ULayer[SDJwtService] = ZLayer.succeed(SDJwtServiceLive())
}
