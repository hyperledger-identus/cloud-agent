package org.hyperledger.identus.vdr.controller.http

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class VdrOperationStatusResponse(
    @description("The current status of the VDR operation")
    status: String,
    @description("Additional details about the operation status, if available")
    details: Option[String],
    @description("The identifier of the submitted transaction, if applicable")
    transactionId: Option[String]
)

object VdrOperationStatusResponse {
  given encoder: JsonEncoder[VdrOperationStatusResponse] = DeriveJsonEncoder.gen[VdrOperationStatusResponse]
  given decoder: JsonDecoder[VdrOperationStatusResponse] = DeriveJsonDecoder.gen[VdrOperationStatusResponse]
  given schema: Schema[VdrOperationStatusResponse] = Schema.derived
}
