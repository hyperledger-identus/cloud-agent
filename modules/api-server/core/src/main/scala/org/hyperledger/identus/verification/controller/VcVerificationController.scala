package org.hyperledger.identus.verification.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.pollux.core.service.verification.VcVerificationServiceError
import org.hyperledger.identus.verification.controller
import zio.*

trait VcVerificationController {

  def verify(request: List[controller.http.VcVerificationRequest])(implicit
      rc: RequestContext
  ): IO[ErrorResponse, List[controller.http.VcVerificationResponse]]
}

object VcVerificationController {
  def toHttpError(error: VcVerificationServiceError): ErrorResponse =
    error match
      case VcVerificationServiceError.UnexpectedError(error) =>
        ErrorResponse.badRequest(detail = Some(s"VC Verification Failed: $error"))

}
