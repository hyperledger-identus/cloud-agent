package org.hyperledger.identus.pollux.prex.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{CollectionStats, PaginationInput}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.pollux.core.service.PresentationExchangeService
import org.hyperledger.identus.pollux.prex.http.{CreatePresentationDefinition, PresentationDefinitionPage}
import org.hyperledger.identus.pollux.prex.PresentationDefinition
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID
import scala.language.implicitConversions

trait PresentationExchangeController {
  def createPresentationDefinition(
      cpd: CreatePresentationDefinition
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationDefinition]

  def getPresentationDefinition(id: UUID): IO[ErrorResponse, PresentationDefinition]

  def listPresentationDefinition(paginationInput: PaginationInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationDefinitionPage]
}

class PresentationExchangeControllerImpl(service: PresentationExchangeService) extends PresentationExchangeController {

  override def createPresentationDefinition(
      cpd: CreatePresentationDefinition
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationDefinition] = {
    val pd: PresentationDefinition = cpd
    service.createPresentationDefinititon(pd).as(pd)
  }

  override def getPresentationDefinition(id: UUID): IO[ErrorResponse, PresentationDefinition] =
    service.getPresentationDefinition(id)

  override def listPresentationDefinition(
      paginationInput: PaginationInput
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, PresentationDefinitionPage] = {
    val uri = rc.request.uri
    val pagination = paginationInput.toPagination
    for {
      pageResult <- service.listPresentationDefinition(offset = paginationInput.offset, limit = paginationInput.limit)
      (items, totalCount) = pageResult
      stats = CollectionStats(totalCount = totalCount, filteredCount = totalCount)
    } yield PresentationDefinitionPage(
      self = uri.toString(),
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      next = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString),
      previous = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString),
      contents = items,
    )
  }

}

object PresentationExchangeControllerImpl {
  def layer: URLayer[PresentationExchangeService, PresentationExchangeController] =
    ZLayer.fromFunction(PresentationExchangeControllerImpl(_))
}
