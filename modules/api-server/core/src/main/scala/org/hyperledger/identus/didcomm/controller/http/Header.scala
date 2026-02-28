package org.hyperledger.identus.didcomm.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Header(kid: String)

object Header {
  given encoder: JsonEncoder[Header] = DeriveJsonEncoder.gen[Header]
  given decoder: JsonDecoder[Header] = DeriveJsonDecoder.gen[Header]
  given schema: Schema[Header] = Schema.derived
}
