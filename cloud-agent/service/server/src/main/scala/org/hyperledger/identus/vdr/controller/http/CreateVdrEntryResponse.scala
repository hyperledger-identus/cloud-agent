package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

final case class CreateVdrEntryResponse(url: String)

object CreateVdrEntryResponse {
  given encoder: JsonEncoder[CreateVdrEntryResponse] = DeriveJsonEncoder.gen[CreateVdrEntryResponse]
  given decoder: JsonDecoder[CreateVdrEntryResponse] = DeriveJsonDecoder.gen[CreateVdrEntryResponse]
  given schema: Schema[CreateVdrEntryResponse] = Schema.derived
}
