package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.util.UUID

trait WalletNonSecretStorage {
  def createWallet(wallet: Wallet, seedDigest: Array[Byte]): UIO[Wallet]
  def findWalletById(walletId: WalletId): UIO[Option[Wallet]]
  def findWalletBySeed(seedDigest: Array[Byte]): UIO[Option[Wallet]]
  def getWallets(walletIds: Seq[WalletId]): UIO[Seq[Wallet]]
  def listWallet(
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): UIO[(Seq[Wallet], RuntimeFlags)]
  def countWalletNotification: URIO[WalletAccessContext, Int]
  def createWalletNotification(
      config: EventNotificationConfig
  ): URIO[WalletAccessContext, Unit]
  def walletNotification: URIO[WalletAccessContext, Seq[EventNotificationConfig]]
  def deleteWalletNotification(id: UUID): URIO[WalletAccessContext, Unit]
}
