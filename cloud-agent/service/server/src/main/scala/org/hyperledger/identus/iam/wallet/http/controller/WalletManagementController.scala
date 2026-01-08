package org.hyperledger.identus.iam.wallet.http.controller

import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{CollectionStats, PaginationInput}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.iam.authentication.oidc.KeycloakEntity
import org.hyperledger.identus.iam.authorization.core.PermissionManagementService
import org.hyperledger.identus.iam.wallet.http.model.{
  CreateWalletRequest,
  CreateWalletUmaPermissionRequest,
  WalletDetail,
  WalletDetailPage
}
import org.hyperledger.identus.shared.models.{HexString, WalletAdministrationContext, WalletId}
import org.hyperledger.identus.shared.models.WalletAdministrationContext.Admin
import zio.*

import java.util.UUID
import scala.language.implicitConversions

trait WalletManagementController {
  def listWallet(
      paginationInput: PaginationInput
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetailPage]
  def getWallet(walletId: UUID)(implicit
      rc: RequestContext
  ): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetail]
  def createWallet(
      request: CreateWalletRequest,
      me: BaseEntity
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetail]
  def createWalletUmaPermission(
      walletId: UUID,
      request: CreateWalletUmaPermissionRequest
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, Unit]
  def deleteWalletUmaPermission(
      walletId: UUID,
      subject: UUID
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, Unit]
}

class WalletManagementControllerImpl(
    walletService: WalletManagementService,
    permissionService: PermissionManagementService[BaseEntity],
) extends WalletManagementController {

  override def listWallet(
      paginationInput: PaginationInput
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetailPage] = {
    val uri = rc.request.uri
    val pagination = paginationInput.toPagination
    for {
      pageResult <- walletService
        .listWallets(offset = paginationInput.offset, limit = paginationInput.limit)
      (items, totalCount) = pageResult
      stats = CollectionStats(totalCount = totalCount, filteredCount = totalCount)
    } yield WalletDetailPage(
      self = uri.toString(),
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      next = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString),
      previous = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString),
      contents = items.map(i => i),
    )
  }

  override def getWallet(
      walletId: UUID
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetail] = {
    for {
      wallet <- walletService
        .findWallet(WalletId.fromUUID(walletId))
        .someOrFail(ErrorResponse.notFound(detail = Some(s"Wallet id $walletId does not exist.")))
    } yield wallet
  }

  override def createWallet(
      request: CreateWalletRequest,
      me: BaseEntity
  )(implicit rc: RequestContext): ZIO[WalletAdministrationContext, ErrorResponse, WalletDetail] = {
    ZIO.serviceWithZIO[WalletAdministrationContext] {
      case WalletAdministrationContext.Admin()        => doCreateWallet(request).map(i => i)
      case WalletAdministrationContext.SelfService(_) =>
        for {
          wallet <- doCreateWallet(request)
          _ <- permissionService
            .grantWalletToUser(wallet.id, me)
            .mapError[ErrorResponse](e => e)
            .provide(ZLayer.succeed(WalletAdministrationContext.Admin())) // First time to use must be admin
        } yield wallet
    }
  }

  override def createWalletUmaPermission(walletId: UUID, request: CreateWalletUmaPermissionRequest)(implicit
      rc: RequestContext
  ): ZIO[WalletAdministrationContext, ErrorResponse, Unit] = {
    val grantee = KeycloakEntity(request.subject)
    permissionService
      .grantWalletToUser(WalletId.fromUUID(walletId), grantee)
      .mapError[ErrorResponse](e => e)
  }

  override def deleteWalletUmaPermission(walletId: UUID, subject: UUID)(implicit
      rc: RequestContext
  ): ZIO[WalletAdministrationContext, ErrorResponse, Unit] = {
    val grantee = KeycloakEntity(subject)
    permissionService
      .revokeWalletFromUser(WalletId.fromUUID(walletId), grantee)
      .mapError[ErrorResponse](e => e)
  }

  private def doCreateWallet(request: CreateWalletRequest): ZIO[WalletAdministrationContext, ErrorResponse, Wallet] = {
    for {
      providedSeed <- request.seed.fold(ZIO.none)(s => extractWalletSeed(s).asSome)
      walletId = request.id.map(WalletId.fromUUID).getOrElse(WalletId.random)
      wallet <- walletService
        .createWallet(Wallet(request.name, walletId), providedSeed)
        .mapError[ErrorResponse](identity)
    } yield wallet
  }

  private def extractWalletSeed(seedHex: String): IO[ErrorResponse, WalletSeed] = {
    ZIO
      .fromTry(HexString.fromString(seedHex))
      .mapError(_.getMessage())
      .map(_.toByteArray)
      .map(WalletSeed.fromByteArray)
      .absolve
      .mapError(e =>
        ErrorResponse.badRequest(detail =
          Some(s"The provided wallet seed is not valid hex string representing a BIP-32 seed. ($e)")
        )
      )
  }

}

object WalletManagementControllerImpl {
  val layer: URLayer[WalletManagementService & PermissionManagementService[BaseEntity], WalletManagementController] =
    ZLayer.fromFunction(WalletManagementControllerImpl(_, _))
}
