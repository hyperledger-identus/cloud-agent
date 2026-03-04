package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.did.core.model.did.DID
import zio.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json

import java.security.PublicKey

opaque type JWT = String

object JWT {
  def apply(value: String): JWT = value

  extension (jwt: JWT) {
    def value: String = jwt
  }

  given JsonEncoder[JWT] = JsonEncoder.string.contramap(jwt => jwt.value)
  given JsonDecoder[JWT] = JsonDecoder.string.map(JWT(_))
}

trait Signer {
  def encode(claim: Json): JWT

  def generateProofForJson(payload: Json, pk: PublicKey): Task[Proof]
}

case class Issuer(did: DID, signer: Signer, publicKey: PublicKey)
