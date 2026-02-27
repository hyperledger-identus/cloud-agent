package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.service.PresentationExchangeServiceError.{
  PresentationDefinitionNotFound,
  PresentationDefinitionValidationError
}
import org.hyperledger.identus.pollux.prex.{PresentationDefinition, PresentationDefinitionError}
import org.hyperledger.identus.shared.models.{Failure, StatusCode, WalletAccessContext}
import zio.*

import java.util.UUID

sealed trait PresentationExchangeServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace = "PresentationExchangeServiceError"
}

object PresentationExchangeServiceError {
  case class PresentationDefinitionNotFound(id: UUID)
      extends PresentationExchangeServiceError(StatusCode.NotFound, s"PresentationDefinition not found with id: $id")

  case class PresentationDefinitionValidationError(error: PresentationDefinitionError)
      extends PresentationExchangeServiceError(
        StatusCode.BadRequest,
        s"PresentationDefinition validation failed: ${error.userFacingMessage}"
      )
}

trait PresentationExchangeService {
  def createPresentationDefinititon(
      pd: PresentationDefinition
  ): ZIO[WalletAccessContext, PresentationDefinitionValidationError, Unit]

  def getPresentationDefinition(
      id: UUID
  ): IO[PresentationDefinitionNotFound, PresentationDefinition]

  def listPresentationDefinition(
      limit: Option[Int],
      offset: Option[Int]
  ): URIO[WalletAccessContext, (Seq[PresentationDefinition], Int)]
}
