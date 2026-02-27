package org.hyperledger.identus.verification.controller.http

import org.hyperledger.identus.pollux.core.service.verification.VcVerificationResult as ServiceVcVerificationResult
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/** Represents the result of a verification performed on a verifiable credential.
  *
  * @param verification
  *   The type of verification that was performed.
  * @param success
  *   Indicates whether the verification was successful.
  */
final case class VcVerificationResult(
    @description("The type of verification that was performed.")
    verification: VcVerification,
    @description("Indicates whether the verification was successful.")
    success: Boolean
)
object VcVerificationResult {
  given encoder: JsonEncoder[VcVerificationResult] =
    DeriveJsonEncoder.gen[VcVerificationResult]

  given decoder: JsonDecoder[VcVerificationResult] =
    DeriveJsonDecoder.gen[VcVerificationResult]

  given schema: Schema[VcVerificationResult] = Schema.derived

  def toService(result: ServiceVcVerificationResult): VcVerificationResult = {
    VcVerificationResult(
      verification = VcVerification.toService(result.verification),
      success = result.success
    )
  }
}
