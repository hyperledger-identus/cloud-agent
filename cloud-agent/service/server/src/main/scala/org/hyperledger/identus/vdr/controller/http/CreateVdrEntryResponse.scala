package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class CreateVdrEntryResponse(url: String)

object CreateVdrEntryResponse {
  given encoder: JsonEncoder[CreateVdrEntryResponse] = DeriveJsonEncoder.gen[CreateVdrEntryResponse]
  given decoder: JsonDecoder[CreateVdrEntryResponse] = DeriveJsonDecoder.gen[CreateVdrEntryResponse]
  given schema: Schema[CreateVdrEntryResponse] = Schema.derived
}
