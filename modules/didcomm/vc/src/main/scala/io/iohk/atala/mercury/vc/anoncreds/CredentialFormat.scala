package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.issuecredential.IssueCredentialIssuedFormat

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#credential-format
  */
final case class CredentialFormat(
    schema_id: String,
    cred_def_id: String,
    rev_reg_id: String,
    values: Seq[Attr],
    // Fields below can depend on Cred Def type
    signature: String,
    signature_correctness_proof: String,
    rev_reg: String,
    witness: String,
)

object CredentialFormat {
  // anoncreds/credential@v1.0
  def format = IssueCredentialIssuedFormat.Anoncred
}

final case class Attr(raw: String, encoded: String)
