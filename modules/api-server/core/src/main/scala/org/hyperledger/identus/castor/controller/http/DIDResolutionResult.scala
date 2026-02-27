package org.hyperledger.identus.castor.controller.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class DIDResolutionResult(
    `@context`: Context,
    didDocument: Option[DIDDocument] = None,
    didDocumentMetadata: DIDDocumentMetadata,
    didResolutionMetadata: DIDResolutionMetadata
)

object DIDResolutionResult {
  given encoder: JsonEncoder[DIDResolutionResult] = DeriveJsonEncoder.gen[DIDResolutionResult]
  given decoder: JsonDecoder[DIDResolutionResult] = DeriveJsonDecoder.gen[DIDResolutionResult]
  given schema: Schema[DIDResolutionResult] = Schema.derived
}
