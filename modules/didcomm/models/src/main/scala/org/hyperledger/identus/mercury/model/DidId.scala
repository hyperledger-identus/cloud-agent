package org.hyperledger.identus.mercury.model

import zio.json.{JsonDecoder, JsonEncoder}

final case class DidId(value: String)
object DidId {
  given encoder: JsonEncoder[DidId] = JsonEncoder[String].contramap(_.value)
  given decoder: JsonDecoder[DidId] = JsonDecoder[String].map(str => DidId(str))
}
