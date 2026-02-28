package org.hyperledger.identus.credentials.core.service.verification

sealed trait VcVerificationServiceError {
  def error: String
}

object VcVerificationServiceError {
  final case class UnexpectedError(error: String) extends VcVerificationServiceError
}
