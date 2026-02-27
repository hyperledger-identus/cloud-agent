package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.repository.PresentationExchangeRepository
import org.hyperledger.identus.pollux.core.service.PresentationExchangeServiceError.{
  PresentationDefinitionNotFound,
  PresentationDefinitionValidationError
}
import org.hyperledger.identus.pollux.prex.{PresentationDefinition, PresentationDefinitionValidator}
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

class PresentationExchangeServiceImpl(validator: PresentationDefinitionValidator, repo: PresentationExchangeRepository)
    extends PresentationExchangeService {

  override def createPresentationDefinititon(
      pd: PresentationDefinition
  ): ZIO[WalletAccessContext, PresentationDefinitionValidationError, Unit] =
    for {
      _ <- validator.validate(pd).mapError(PresentationDefinitionValidationError(_))
      _ <- repo.createPresentationDefinition(pd)
    } yield ()

  override def getPresentationDefinition(
      id: UUID
  ): IO[PresentationDefinitionNotFound, PresentationDefinition] =
    repo.findPresentationDefinition(id).someOrFail(PresentationDefinitionNotFound(id))

  override def listPresentationDefinition(
      limit: Option[Int],
      offset: Option[Int]
  ): URIO[WalletAccessContext, (Seq[PresentationDefinition], Int)] =
    repo.listPresentationDefinition(offset = offset, limit = limit)

}

object PresentationExchangeServiceImpl {
  def layer: URLayer[PresentationDefinitionValidator & PresentationExchangeRepository, PresentationExchangeService] =
    ZLayer.fromFunction(PresentationExchangeServiceImpl(_, _))
}
