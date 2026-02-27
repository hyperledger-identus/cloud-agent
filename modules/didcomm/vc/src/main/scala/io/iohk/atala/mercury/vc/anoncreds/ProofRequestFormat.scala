package org.hyperledger.identus.vc.anoncreds

import org.hyperledger.identus.mercury.protocol.presentproof.PresentCredentialRequestFormat

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0771-anoncreds-attachments/README.md#proof-request-format
  */
final case class ProofRequestFormat(
    nonce: String, // : "2934823091873049823740198370q23984710239847",
    name: String, // :"proof_req_1",
    version: String, // :"0.1",
    requested_attributes: Map[String, AttrReferent],
    requested_predicates: Map[String, Predicate]
)

object ProofRequestFormat {
  // anoncreds/proof-request@v1.0
  def format = PresentCredentialRequestFormat.Anoncred
}

trait AttrReferent

/** {{{
  * {"name":"phone"}
  * }}}
  */
final case class AttrReferentName(name: String) extends AttrReferent

/** {{{
  * {"names": ["name", "height"], "restrictions": <restrictions specifying government-issued ID>}
  * }}}
  */
final case class AttrReferentNames(names: Seq[String], restrictions: String) extends AttrReferent

/** {{{
  * {"name":"age","p_type":">=","p_value":18}
  * }}}
  */
final case class Predicate(name: String, p_type: String, p_value: Long)
