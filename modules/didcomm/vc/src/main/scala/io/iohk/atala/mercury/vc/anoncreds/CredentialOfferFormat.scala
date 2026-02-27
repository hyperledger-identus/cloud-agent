package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.issuecredential.IssueCredentialOfferFormat

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#credential-offer-format
  * @see
  *   https://hyperledger.github.io/anoncreds-spec/#credential-offer
  */
final case class CredentialOfferFormat(
    schema_id: String,
    cred_def_id: String,
    nonce: String,
    key_correctness_proof: KeyCorrectnessProof,
)

object CredentialOfferFormat {
  // anoncreds/credential-offer@v1.0
  def format = IssueCredentialOfferFormat.Anoncred
}

case class KeyCorrectnessProof(
    c: String, // The String of a BigNumber
    xz_cap: String,
    xr_cap: Seq[XrCap]
)

/** The encoded MUST loke like a array of two
  * {{{
  *  [attributeName,value]
  * }}}
  */
case class XrCap(attributeName: String, value: String)
