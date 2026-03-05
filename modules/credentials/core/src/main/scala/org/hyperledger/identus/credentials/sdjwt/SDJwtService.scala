package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.crypto.{Ed25519PrivateKey, Ed25519PublicKey}
import zio.json.ast.Json

trait SDJwtService {
  def issueCredential(issuerKey: Ed25519PrivateKey, claims: String): CredentialCompact
  def issueCredential(issuerKey: Ed25519PrivateKey, claims: String, holderJwk: String): CredentialCompact
  def createPresentation(sdjwt: CredentialCompact, claimsToDisclose: String): PresentationCompact
  def createPresentation(
      sdjwt: CredentialCompact,
      claimsToDisclose: String,
      nonce: String,
      aud: String,
      holderKey: Ed25519PrivateKey,
  ): PresentationCompact

  /** Verify an SD-JWT presentation and return the disclosed claims on success. */
  def verifyPresentation(
      issuerPublicKey: Ed25519PublicKey,
      presentation: PresentationCompact,
  ): Either[String, Json.Obj]
}
