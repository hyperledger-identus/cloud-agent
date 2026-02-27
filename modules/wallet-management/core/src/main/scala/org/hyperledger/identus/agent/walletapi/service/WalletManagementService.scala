package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.{Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceError.{
  DuplicatedWalletId,
  DuplicatedWalletSeed,
  TooManyPermittedWallet,
  TooManyWebhookError
}
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.models.*
import zio.*

import java.util.UUID
import scala.language.implicitConversions

sealed trait WalletManagementServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "WalletManagementServiceError"
}

object WalletManagementServiceError {
  final case class TooManyWebhookError(walletId: WalletId, limit: Int)
      extends WalletManagementServiceError(
        StatusCode.UnprocessableContent,
        s"The maximum number of webhooks has been reached for the wallet: walletId=$walletId, limit=$limit"
      )

  final case class DuplicatedWalletId(walletId: WalletId)
      extends WalletManagementServiceError(
        StatusCode.UnprocessableContent,
        s"A wallet with the same ID already exist: walletId=$walletId"
      )
  final case class DuplicatedWalletSeed()
      extends WalletManagementServiceError(
        StatusCode.UnprocessableContent,
        s"A wallet with the same seed already exist"
      )
  final case class TooManyPermittedWallet()
      extends WalletManagementServiceError(
        StatusCode.BadRequest,
        s"The operation is not allowed because wallet access already exists for the current user"
      )
}

trait WalletManagementService {
  def createWallet(
      wallet: Wallet,
      seed: Option[WalletSeed] = None
  ): ZIO[WalletAdministrationContext, TooManyPermittedWallet | DuplicatedWalletId | DuplicatedWalletSeed, Wallet]

  def findWallet(walletId: WalletId): URIO[WalletAdministrationContext, Option[Wallet]]

  def getWallets(walletIds: Seq[WalletId]): URIO[WalletAdministrationContext, Seq[Wallet]]

  /** @return A tuple containing a list of items and a count of total items */
  def listWallets(
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): URIO[WalletAdministrationContext, (Seq[Wallet], Int)]

  def listWalletNotifications: URIO[WalletAccessContext, Seq[EventNotificationConfig]]

  def createWalletNotification(
      config: EventNotificationConfig
  ): ZIO[WalletAccessContext, TooManyWebhookError, Unit]

  def deleteWalletNotification(id: UUID): URIO[WalletAccessContext, Unit]
}
