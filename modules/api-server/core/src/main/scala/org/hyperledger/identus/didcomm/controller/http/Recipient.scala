package org.hyperledger.identus.didcomm.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Recipient(encrypted_key: String, header: Header)

object Recipient {
  given encoder: JsonEncoder[Recipient] = DeriveJsonEncoder.gen[Recipient]
  given decoder: JsonDecoder[Recipient] = DeriveJsonDecoder.gen[Recipient]
  given schema: Schema[Recipient] = Schema.derived
}
