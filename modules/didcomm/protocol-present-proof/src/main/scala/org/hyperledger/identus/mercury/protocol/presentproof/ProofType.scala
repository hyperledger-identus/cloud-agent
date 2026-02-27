package org.hyperledger.identus.mercury.protocol.presentproof

import org.hyperledger.identus.mercury.model.DidId
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ProofType(
    schema: String, // Schema ID EX: https://schema.org/Person
    requiredFields: Option[Seq[String]], // ["email"]
    trustIssuers: Option[Seq[DidId]] // ["did:prism:123321123"]
)
object ProofType {
  given JsonEncoder[ProofType] = DeriveJsonEncoder.gen
  given JsonDecoder[ProofType] = DeriveJsonDecoder.gen
}
