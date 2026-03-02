package org.hyperledger.identus.didcomm.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class DIDCommMessage(
    ciphertext: String,
    `protected`: String,
    recipients: List[Recipient],
    tag: String,
    iv: String
)

object DIDCommMessage {
  given encoder: JsonEncoder[DIDCommMessage] = DeriveJsonEncoder.gen[DIDCommMessage]
  given decoder: JsonDecoder[DIDCommMessage] = DeriveJsonDecoder.gen[DIDCommMessage]
  given schema: Schema[DIDCommMessage] = Schema.derived
}
