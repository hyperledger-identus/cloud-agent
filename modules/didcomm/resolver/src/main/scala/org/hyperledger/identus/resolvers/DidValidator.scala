package org.hyperledger.identus.resolvers

import org.hyperledger.identus.mercury.model.DidId

object DidValidator extends DidValidator
trait DidValidator {
  val regexAny = "^did:(.*):(.*)$".r

  /** Ex: did:prism:66940961cc0f6a884ff5876992991b994ca518aa34b3bacfd15f2b51a7b042cf
    */
  val regexPRISM = "^did:prism:(.*)$".r

  /** Ex:
    * did:peer:2.Ez6LSeSTchYyPTBk131pKECXWP7t1CYG2RMgRE2KWoiWi962w.Vz6MkmLJC9YyMerhFD831jrbVAo8rHXiBvDV6UKnt8xzQY7MJ.SeyJ0IjoiZG0iLCJzIjoiaHR0cDovL2xvY2FsaG9zdEw5OTk5IiwiciI6W10sImEiOlsiZGlkY29tbS92MiJdfQ
    */
  val regexPeer =
    "^did:peer:(([01](z)([1-9a-km-zA-HJ-NP-Z]{46,47}))|(2((\\.[AEVID](z)([1-9a-km-zA-HJ-NP-Z]{46,47}))+(\\.(S)[0-9a-zA-Z=]*)?)))$".r

  def isDidPRISM(did: String) = did match {
    case regexPRISM(id) => true
    case _              => false
  }
  def isDidPeer(did: String) = did match {
    case regexPeer(id, _*) => true
    case _                 => false
  }

  def validDID(did: DidId): Boolean = validDID(did.value)
  def validDID(did: String): Boolean = did match {
    case regexAny(method, id) => true
    case _                    => false
  }

  def supportedDid(did: DidId): Boolean = supportedDid(did.value)
  def supportedDid(did: String): Boolean = did match
    case regexPRISM(id)                  => true
    case regexPeer(id, _*)               => true
    case regexAny("example", "alice")    => true // for debug
    case regexAny("example", "mediator") => true // for debug
    case regexAny("example", "bob")      => true // for debug
    case regexAny(method, id)            => false
    case _                               => false // NOT a DID

}
