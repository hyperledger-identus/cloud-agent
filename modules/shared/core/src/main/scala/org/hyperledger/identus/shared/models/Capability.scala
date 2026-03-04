package org.hyperledger.identus.shared.models

/** A capability that a module can provide or require.
  * @param contract
  *   the contract identifier (e.g. "CredentialSigner")
  * @param variant
  *   optional variant (e.g. "eddsa", "es256"). None means "any variant".
  */
case class Capability(contract: String, variant: Option[String] = None):
  /** Returns true if this capability satisfies the given requirement. A requirement with variant=None is satisfied by any
    * variant of the same contract.
    */
  def satisfies(requirement: Capability): Boolean =
    contract == requirement.contract &&
      (requirement.variant.isEmpty || variant == requirement.variant)

enum Cardinality:
  case ExactlyOne
  case AtLeastOne
  case ZeroOrMore
  case ZeroOrOne

trait Contract:
  def id: String
  def cardinality: Cardinality
