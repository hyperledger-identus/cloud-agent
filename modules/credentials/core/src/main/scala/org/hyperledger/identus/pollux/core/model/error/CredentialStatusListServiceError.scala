package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.Role
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

import java.util.UUID

sealed trait CredentialStatusListServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "CredentialStatusListError"
}

object CredentialStatusListServiceError {
  final case class StatusListNotFound(id: UUID)
      extends CredentialStatusListServiceError(
        StatusCode.NotFound,
        s"There is no credential status record matching the provided identifier: id=$id"
      )

  final case class StatusListNotFoundForIssueCredentialRecord(id: DidCommID)
      extends CredentialStatusListServiceError(
        StatusCode.NotFound,
        s"There is no credential status record matching the provided issue credential record identifier: id=${id.value}"
      )

  final case class InvalidRoleForOperation(role: Role)
      extends CredentialStatusListServiceError(
        StatusCode.UnprocessableContent,
        s"The role is invalid to complete the request operation: role=${role.toString}"
      )
}
