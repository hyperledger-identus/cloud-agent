package org.hyperledger.identus.oid4vci.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class NonceResponse(
    nonce: String,
    nonceExpiresIn: Long = 86400
) {
  require(nonce.nonEmpty, "nonce must not be empty")
  require(nonceExpiresIn > 0, "nonceExpiresIn must be greater than 0")
}

object NonceResponse {
  given schema: Schema[NonceResponse] = Schema.derived
  given encoder: JsonEncoder[NonceResponse] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[NonceResponse] = DeriveJsonDecoder.gen
}
