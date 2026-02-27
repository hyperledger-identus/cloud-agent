package org.hyperledger.identus.event.controller

import org.hyperledger.identus.agent.walletapi.service.WalletManagementService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{CollectionStats, PaginationInput}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.event.controller.http.{
  CreateWebhookNotification,
  WebhookNotification,
  WebhookNotificationPage
}
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.net.URI
import java.util.UUID
import scala.language.implicitConversions

trait EventController {
  def createWebhookNotification(
      request: CreateWebhookNotification
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, WebhookNotification]

  def listWebhookNotifications(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, WebhookNotificationPage]

  def deleteWebhookNotification(id: UUID)(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Unit]
}

class EventControllerImpl(service: WalletManagementService) extends EventController {

  override def createWebhookNotification(
      request: CreateWebhookNotification
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, WebhookNotification] = {
    for {
      url <- ZIO
        .attempt(new URI(request.url).toURL())
        .mapError(e => ErrorResponse.badRequest(detail = Some(e.toString())))
      notificationConfig <- EventNotificationConfig.applyWallet(url, request.customHeaders.getOrElse(Map.empty))
      _ <- service
        .createWalletNotification(notificationConfig)
        .mapError[ErrorResponse](e => e)
    } yield notificationConfig
  }

  override def listWebhookNotifications(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, WebhookNotificationPage] = {
    val uri = rc.request.uri
    // No pagination in DB as number of webhook is limited
    // Return paginated result for consistency and to make it future-proof
    val pagination = PaginationInput().toPagination
    for {
      items <- service.listWalletNotifications
      totalCount = items.length
      stats = CollectionStats(totalCount = totalCount, filteredCount = totalCount)
    } yield WebhookNotificationPage(
      self = uri.toString(),
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      next = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString),
      previous = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString),
      contents = items.map(i => i),
    )
  }

  override def deleteWebhookNotification(
      id: UUID
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Unit] =
    service.deleteWalletNotification(id)

}

object EventControllerImpl {
  val layer: URLayer[WalletManagementService, EventControllerImpl] = ZLayer.fromFunction(EventControllerImpl(_))
}
