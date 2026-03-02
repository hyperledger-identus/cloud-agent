package org.hyperledger.identus.did.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{CollectionStats, PaginationInput}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.did.controller.http.{
  CreateManagedDIDResponse,
  CreateManagedDidRequest,
  DIDOperationResponse,
  ManagedDID,
  ManagedDIDPage,
  UpdateManagedDIDRequest
}
import org.hyperledger.identus.did.core.model.did.PrismDID
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.shared.utils.Traverse.*
import org.hyperledger.identus.wallet.model.ManagedDIDDetail
import org.hyperledger.identus.wallet.service.ManagedDIDService
import zio.*

trait DIDRegistrarController {
  def listManagedDid(paginationInput: PaginationInput)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, ManagedDIDPage]

  def createManagedDid(request: CreateManagedDidRequest)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CreateManagedDIDResponse]

  def getManagedDid(did: String)(using rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, ManagedDID]

  def publishManagedDid(did: String)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse]

  def updateManagedDid(did: String, updateRequest: UpdateManagedDIDRequest)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse]

  def deactivateManagedDid(did: String)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse]
}

class DIDRegistrarControllerImpl(service: ManagedDIDService) extends DIDRegistrarController {

  import scala.language.implicitConversions

  override def listManagedDid(
      paginationInput: PaginationInput
  )(using rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, ManagedDIDPage] = {
    val uri = rc.request.uri
    val pagination = paginationInput.toPagination
    for {
      pageResult <- service
        .listManagedDIDPage(offset = pagination.offset, limit = pagination.limit)
        .mapError[ErrorResponse](e => e)
      (items, totalCount) = pageResult
      stats = CollectionStats(totalCount = totalCount, filteredCount = totalCount)
    } yield ManagedDIDPage(
      self = uri.toString(),
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      next = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString),
      previous = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString),
      contents = items.map(i => i),
    )
  }

  override def createManagedDid(createManagedDidRequest: CreateManagedDidRequest)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CreateManagedDIDResponse] = {
    for {
      didTemplate <- ZIO
        .fromEither(createManagedDidRequest.documentTemplate.toDomain)
        .mapError(e => ErrorResponse.unprocessableEntity(detail = Some(e)))
      longFormDID <- service
        .createAndStoreDID(didTemplate)
        .mapError[ErrorResponse](e => e)
    } yield CreateManagedDIDResponse(longFormDid = longFormDID.toString)
  }

  override def getManagedDid(
      did: String
  )(using rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, ManagedDID] = {
    for {
      prismDID <- extractPrismDID(did)
      didDetail <- service
        .getManagedDIDState(prismDID.asCanonical)
        .mapError[ErrorResponse](e => e)
        .someOrFail(ErrorResponse.notFound(detail = Some(s"DID $did was not found in the storage")))
        .map(state => ManagedDIDDetail(prismDID.asCanonical, state))
    } yield didDetail
  }

  override def publishManagedDid(
      did: String
  )(using rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse] = {
    for {
      prismDID <- extractPrismDID(did)
      outcome <- service
        .publishStoredDID(prismDID.asCanonical)
        .mapError[ErrorResponse](e => e)
    } yield outcome
  }

  override def updateManagedDid(did: String, updateRequest: UpdateManagedDIDRequest)(using
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse] = {
    for {
      prismDID <- extractPrismDID(did)
      actions <- ZIO
        .fromEither(updateRequest.actions.traverse(_.toDomain))
        .mapError(e => ErrorResponse.badRequest(detail = Some(e)))
      outcome <- service
        .updateManagedDID(prismDID.asCanonical, actions)
        .mapError[ErrorResponse](e => e)
    } yield outcome
  }

  override def deactivateManagedDid(
      did: String
  )(using rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, DIDOperationResponse] = {
    for {
      prismDID <- extractPrismDID(did)
      outcome <- service
        .deactivateManagedDID(prismDID.asCanonical)
        .mapError[ErrorResponse](e => e)
    } yield outcome
  }

  private def extractPrismDID(did: String): IO[ErrorResponse, PrismDID] =
    ZIO.fromEither(PrismDID.fromString(did)).mapError(e => ErrorResponse.badRequest(detail = Some(e.toString)))

}

object DIDRegistrarControllerImpl {
  val layer: URLayer[ManagedDIDService, DIDRegistrarController] = ZLayer.fromFunction(DIDRegistrarControllerImpl(_))
}
