package org.hyperledger.identus.presentproof.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.presentproof.controller.http.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.ZIO

import java.util.UUID
import scala.util.Try

trait PresentProofController {
  def requestPresentation(
      requestPresentationInput: RequestPresentationInput
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus]

  def getPresentations(
      paginationInput: PaginationInput,
      thid: Option[String]
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatusPage]

  def getPresentation(id: UUID)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus]

  def updatePresentation(id: UUID, requestPresentationAction: RequestPresentationAction)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus]

  def createOOBRequestPresentationInvitation(
      request: RequestPresentationInput
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus]

  def acceptRequestPresentationInvitation(
      request: AcceptRequestPresentationInvitation
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus]
}

object PresentProofController {
  def toDidCommID(str: String): ZIO[Any, ErrorResponse, org.hyperledger.identus.pollux.core.model.DidCommID] =
    ZIO
      .fromTry(Try(org.hyperledger.identus.pollux.core.model.DidCommID(str)))
      .mapError(e => ErrorResponse.badRequest(s"Error parsing string as DidCommID: ${e.getMessage}"))
}
