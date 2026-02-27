package org.hyperledger.identus.mercury

import com.nimbusds.jose.jwk.*
import org.hyperledger.identus.mercury.model.DidId

/** Represente a Decentralized Identifier with secrets keys */
trait DidAgent {
  def id: DidId
  def jwkForKeyAgreement: Seq[OctetKeyPair]
  def jwkForKeyAuthentication: Seq[OctetKeyPair]
}
