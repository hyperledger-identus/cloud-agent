package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.issuecredential.IssueCredentialRequestFormat

/** https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#credential-request-format
  */
final case class CredentialRequestFormat(
    entropy: String,
    cred_def_id: String,
    // Fields below can depend on Cred Def type
    blinded_ms: String,
    blinded_ms_correctness_proof: String,
    nonce: String,
)

object CredentialRequestFormat {
  // anoncreds/credential-request@v1.0
  def format = IssueCredentialRequestFormat.Anoncred
}
