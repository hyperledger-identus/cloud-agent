package org.hyperledger.identus.oid4vci.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class NonceRequest(issuerState: String)

object NonceRequest {
  given schema: Schema[NonceRequest] = Schema.derived
  given encoder: JsonEncoder[NonceRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[NonceRequest] = DeriveJsonDecoder.gen
}
