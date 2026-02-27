package org.hyperledger.identus.pollux.core.service.verification

import zio.*

trait VcVerificationService {
  def verify(request: List[VcVerificationRequest]): IO[VcVerificationServiceError, List[VcVerificationResult]]
}

final case class VcVerificationRequest(
    credential: String,
    verification: VcVerification,
)

final case class VcVerificationResult(
    credential: String,
    verification: VcVerification,
    success: Boolean
)
