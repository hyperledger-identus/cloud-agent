package org.hyperledger.identus.didcomm

import com.nimbusds.jose.jwk.*
import org.hyperledger.identus.didcomm.model.DidId

/** Represente a Decentralized Identifier with secrets keys */
trait DidAgent {
  def id: DidId
  def jwkForKeyAgreement: Seq[OctetKeyPair]
  def jwkForKeyAuthentication: Seq[OctetKeyPair]
}
