package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.issuecredential.IssueCredentialRequestFormat

type TODO = Any

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#proof-format
  */
final case class ProofFormat(
    proof: Proof,
    requested_proof: TODO,
    identifiers: Seq[Identifier],
)

object ProofFormat {
  // anoncreds/proof@v1.0
  def format = IssueCredentialRequestFormat.Anoncred
}

final case class Proof(
    proof: Seq[TODO],
    aggregated_proof: TODO
)

final case class Identifier(
    schema_id: String,
    cred_def_id: String,
    rev_reg_id: Option[String],
    timestamp: Option[String],
)
