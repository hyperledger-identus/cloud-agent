package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.issuecredential.IssueCredentialProposeFormat

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#credential-filter-format
  */
final case class CredentialFilterFormat(
    schema_issuer_id: Option[String],
    schema_name: Option[String],
    schema_version: Option[String],
    schema_id: Option[String],
    issuer_id: Option[String],
    cred_def_id: Option[String],
)

object CredentialFilterFormat {
  // anoncreds/credential-filter@v1.0
  def format = IssueCredentialProposeFormat.Anoncred
}
