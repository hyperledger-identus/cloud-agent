package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.shared.models.{Failure, StatusCode}

import java.util.UUID

sealed trait VerificationPolicyError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace = "CredentialSchema"
}

object VerificationPolicyError {
  final case class NotFoundError(id: UUID)
      extends VerificationPolicyError(StatusCode.NotFound, s"VerificationPolicy is not found by $id")
}
