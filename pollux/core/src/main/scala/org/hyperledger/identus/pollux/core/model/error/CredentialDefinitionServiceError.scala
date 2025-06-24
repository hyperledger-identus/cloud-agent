package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.*
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

import java.util.UUID

sealed trait CredentialDefinitionServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace = "CredentialDefinition"
}

final case class CredentialDefinitionGuidNotFoundError(guid: UUID)
    extends CredentialDefinitionServiceError(
      StatusCode.NotFound,
      s"Credential Definition record cannot be found by `guid`=$guid"
    )

final case class CredentialDefinitionCreationError(msg: String)
    extends CredentialDefinitionServiceError(
      StatusCode.BadRequest,
      s"Credential Definition Creation Error=${msg}"
    )

final case class CredentialDefinitionValidationError(cause: CredentialSchemaError)
    extends CredentialDefinitionServiceError(
      cause.statusCode,
      s"Credential Schema Validation Error=${cause.userFacingMessage}"
    )
