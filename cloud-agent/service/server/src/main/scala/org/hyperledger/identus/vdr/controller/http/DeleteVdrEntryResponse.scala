package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class DeleteVdrEntryResponse(
    @description("Identifier of the deletion operation, if provided by the driver")
    @encodedExample("334b5860723bd190eca2187430ea638071ac0406a49cb22c8a7c90c24fa1df48")
    operationId: Option[String] = None
)

object DeleteVdrEntryResponse {
  given encoder: JsonEncoder[DeleteVdrEntryResponse] = DeriveJsonEncoder.gen[DeleteVdrEntryResponse]
  given decoder: JsonDecoder[DeleteVdrEntryResponse] = DeriveJsonDecoder.gen[DeleteVdrEntryResponse]
  given schema: Schema[DeleteVdrEntryResponse] = Schema.derived
}
