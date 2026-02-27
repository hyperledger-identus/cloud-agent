package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class VdrOperationStatusResponse(
    status: String,
    details: Option[String],
    transactionId: Option[String]
)

object VdrOperationStatusResponse {
  given encoder: JsonEncoder[VdrOperationStatusResponse] = DeriveJsonEncoder.gen[VdrOperationStatusResponse]
  given decoder: JsonDecoder[VdrOperationStatusResponse] = DeriveJsonDecoder.gen[VdrOperationStatusResponse]
  given schema: Schema[VdrOperationStatusResponse] = Schema.derived
}
