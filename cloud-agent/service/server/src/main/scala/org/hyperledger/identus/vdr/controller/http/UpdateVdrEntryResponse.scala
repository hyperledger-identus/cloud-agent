package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

final case class UpdateVdrEntryResponse(url: Option[String])

object UpdateVdrEntryResponse {
  given encoder: JsonEncoder[UpdateVdrEntryResponse] = DeriveJsonEncoder.gen[UpdateVdrEntryResponse]
  given decoder: JsonDecoder[UpdateVdrEntryResponse] = DeriveJsonDecoder.gen[UpdateVdrEntryResponse]
  given schema: Schema[UpdateVdrEntryResponse] = Schema.derived
}
