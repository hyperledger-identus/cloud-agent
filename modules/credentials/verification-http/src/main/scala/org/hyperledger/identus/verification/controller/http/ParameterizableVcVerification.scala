package org.hyperledger.identus.verification.controller.http

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/** Represents a parameterizable verification to be performed on a verifiable credential.
  *
  * @param verification
  *   The type of verification to perform.
  * @param parameter
  *   Optional parameter for the verification.
  */
final case class ParameterizableVcVerification(
    @description("The type of verification to perform.")
    verification: VcVerification,
    @description("Optional parameter for the verification.")
    parameter: Option[VcVerificationParameter]
)

object ParameterizableVcVerification {
  given encoder: JsonEncoder[ParameterizableVcVerification] =
    DeriveJsonEncoder.gen[ParameterizableVcVerification]

  given decoder: JsonDecoder[ParameterizableVcVerification] =
    DeriveJsonDecoder.gen[ParameterizableVcVerification]

  given schema: Schema[ParameterizableVcVerification] = Schema.derived
}
