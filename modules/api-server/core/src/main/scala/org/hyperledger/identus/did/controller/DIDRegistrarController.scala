package org.hyperledger.identus.did.controller

import org.hyperledger.identus.wallet.model.error.{
  CreateManagedDIDError,
  GetManagedDIDError,
  PublishManagedDIDError,
  UpdateManagedDIDError
}
import org.hyperledger.identus.wallet.model.ManagedDIDDetail
import org.hyperledger.identus.wallet.service.ManagedDIDService
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
import zio.*

import scala.language.implicitConversions

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

object DIDRegistrarController {
  given Conversion[GetManagedDIDError, ErrorResponse] = {
    case GetManagedDIDError.OperationError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.toString))
    case GetManagedDIDError.ResolutionError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.toString))
    case GetManagedDIDError.WalletStorageError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.getMessage))
  }

  given Conversion[CreateManagedDIDError, ErrorResponse] = {
    case CreateManagedDIDError.InvalidArgument(msg) =>
      ErrorResponse.unprocessableEntity(detail = Some(msg))
    case CreateManagedDIDError.WalletStorageError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.getMessage))
    case CreateManagedDIDError.InvalidOperation(e) =>
      ErrorResponse.unprocessableEntity(detail = Some(e.toString))
  }

  given Conversion[PublishManagedDIDError, ErrorResponse] = {
    case PublishManagedDIDError.DIDNotFound(did) =>
      ErrorResponse.notFound(detail = Some(s"DID not found: $did"))
    case PublishManagedDIDError.WalletStorageError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.getMessage))
    case PublishManagedDIDError.OperationError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.toString))
    case PublishManagedDIDError.CryptographyError(e) =>
      ErrorResponse.internalServerError(detail = Some(e.toString))
  }

  given Conversion[UpdateManagedDIDError, ErrorResponse] = {
    case UpdateManagedDIDError.DIDNotFound(did) =>
      ErrorResponse.notFound(detail = Some(s"DID not found: $did"))
    case UpdateManagedDIDError.DIDNotPublished(did) =>
      ErrorResponse.conflict(detail = Some(s"DID not published: $did"))
    case UpdateManagedDIDError.DIDAlreadyDeactivated(did) =>
      ErrorResponse.conflict(detail = Some(s"DID already deactivated: $did"))
    case UpdateManagedDIDError.InvalidArgument(msg) =>
      ErrorResponse.badRequest(detail = Some(msg))
    case UpdateManagedDIDError.MultipleInflightUpdateNotAllowed(did) =>
      ErrorResponse.conflict(detail = Some(s"Multiple in-flight update operations are not allowed: $did"))
    case e => ErrorResponse.internalServerError(detail = Some(e.toString))
  }
}

class DIDRegistrarControllerImpl(service: ManagedDIDService) extends DIDRegistrarController {

  import DIDRegistrarController.given

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
