package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.*
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

import java.util.UUID

sealed trait CredentialSchemaServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace = "CredentialSchema"
}

final case class CredentialSchemaGuidNotFoundError(guid: UUID)
    extends CredentialSchemaServiceError(
      StatusCode.NotFound,
      s"Credential Schema record cannot be found by `guid`=$guid"
    )

final case class CredentialSchemaIdNotFoundError(id: UUID)
    extends CredentialSchemaServiceError(
      StatusCode.NotFound,
      s"Credential Schema record cannot be found by `id`=$id"
    )

final case class CredentialSchemaUpdateError(id: UUID, version: String, author: String, message: String)
    extends CredentialSchemaServiceError(
      StatusCode.BadRequest,
      s"Credential schema update error: id=$id, version=$version, author=$author, msg=$message"
    )

final case class CredentialSchemaValidationError(cause: CredentialSchemaError)
    extends CredentialSchemaServiceError(
      StatusCode.BadRequest,
      s"Credential Schema Validation Error=${cause.userFacingMessage}"
    )
