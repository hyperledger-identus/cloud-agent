package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.{Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceError.{
  DuplicatedWalletId,
  DuplicatedWalletSeed,
  TooManyPermittedWallet,
  TooManyWebhookError
}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceImpl.MAX_WEBHOOK_PER_WALLET
import org.hyperledger.identus.agent.walletapi.storage.{WalletNonSecretStorage, WalletSecretStorage}
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext, WalletId}
import zio.*

import java.util.UUID
import scala.language.implicitConversions

class WalletManagementServiceImpl(
    apollo: Apollo,
    nonSecretStorage: WalletNonSecretStorage,
    secretStorage: WalletSecretStorage,
) extends WalletManagementService {

  override def createWallet(
      wallet: Wallet,
      seed: Option[WalletSeed]
  ): ZIO[WalletAdministrationContext, TooManyPermittedWallet | DuplicatedWalletId | DuplicatedWalletSeed, Wallet] =
    for {
      _ <- ZIO.serviceWithZIO[WalletAdministrationContext] {
        case WalletAdministrationContext.Admin()                                                   => ZIO.unit
        case WalletAdministrationContext.SelfService(permittedWallets) if permittedWallets.isEmpty => ZIO.unit
        case WalletAdministrationContext.SelfService(_)                                            =>
          ZIO.fail(TooManyPermittedWallet())
      }
      seed <- seed.fold(
        apollo.secp256k1.randomBip32Seed
          .flatMap { case (bytes, _) =>
            ZIO
              .fromEither(WalletSeed.fromByteArray(bytes))
              .orDieWith(msg => Exception(s"Wallet seed generation failed: $msg"))
          }
      )(ZIO.succeed)
      _ <- nonSecretStorage.findWalletBySeed(seed.sha256Digest).flatMap {
        case Some(w) => ZIO.fail(DuplicatedWalletSeed())
        case None    => ZIO.unit
      }
      _ <- nonSecretStorage.findWalletById(wallet.id).flatMap {
        case Some(w) => ZIO.fail(DuplicatedWalletId(wallet.id))
        case None    => ZIO.unit
      }
      createdWallet <- nonSecretStorage
        .createWallet(wallet, seed.sha256Digest)
      _ <- secretStorage
        .setWalletSeed(seed)
        .provide(ZLayer.succeed(WalletAccessContext(wallet.id)))
    } yield createdWallet

  override def findWallet(
      walletId: WalletId
  ): URIO[WalletAdministrationContext, Option[Wallet]] = {
    ZIO
      .serviceWith[WalletAdministrationContext](_.isAuthorized(walletId))
      .flatMap {
        case true  => nonSecretStorage.findWalletById(walletId)
        case false => ZIO.none
      }
  }

  override def getWallets(
      walletIds: Seq[WalletId]
  ): URIO[WalletAdministrationContext, Seq[Wallet]] = {
    ZIO
      .serviceWith[WalletAdministrationContext](ctx => walletIds.filter(ctx.isAuthorized))
      .flatMap { filteredIds => nonSecretStorage.getWallets(filteredIds) }
  }

  override def listWallets(
      offset: Option[Int],
      limit: Option[Int]
  ): URIO[WalletAdministrationContext, (Seq[Wallet], Int)] =
    ZIO.serviceWithZIO[WalletAdministrationContext] {
      case WalletAdministrationContext.Admin() =>
        nonSecretStorage
          .listWallet(offset = offset, limit = limit)
      case WalletAdministrationContext.SelfService(permittedWallets) =>
        nonSecretStorage
          .getWallets(permittedWallets)
          .map(wallets => (wallets, wallets.length))
    }

  override def listWalletNotifications: URIO[WalletAccessContext, Seq[EventNotificationConfig]] =
    nonSecretStorage.walletNotification

  override def createWalletNotification(
      config: EventNotificationConfig
  ): ZIO[WalletAccessContext, TooManyWebhookError, Unit] =
    for {
      count <- nonSecretStorage.countWalletNotification
      _ <-
        if (count < MAX_WEBHOOK_PER_WALLET) nonSecretStorage.createWalletNotification(config)
        else ZIO.fail(TooManyWebhookError(config.walletId, MAX_WEBHOOK_PER_WALLET))
    } yield ()

  override def deleteWalletNotification(id: UUID): URIO[WalletAccessContext, Unit] =
    nonSecretStorage
      .deleteWalletNotification(id)

}

object WalletManagementServiceImpl {
  val MAX_WEBHOOK_PER_WALLET = 1
  val layer: URLayer[Apollo & WalletNonSecretStorage & WalletSecretStorage, WalletManagementService] = {
    ZLayer.fromFunction(WalletManagementServiceImpl(_, _, _))
  }
}
