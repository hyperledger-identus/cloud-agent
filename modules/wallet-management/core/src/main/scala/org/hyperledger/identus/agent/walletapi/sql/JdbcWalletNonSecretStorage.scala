package org.hyperledger.identus.agent.walletapi.sql

import doobie.*
import doobie.util.transactor.Transactor
import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.agent.walletapi.sql.model.{WalletNotificationSql, WalletSql}
import org.hyperledger.identus.agent.walletapi.sql.model as db
import org.hyperledger.identus.agent.walletapi.storage.WalletNonSecretStorage
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.util.UUID

class JdbcWalletNonSecretStorage(xa: Transactor[ContextAwareTask]) extends WalletNonSecretStorage {

  override def createWallet(wallet: Wallet, seedDigest: Array[Byte]): UIO[Wallet] = {
    WalletSql
      .insert(db.Wallet.from(wallet, seedDigest))
      .transactWithoutContext(xa)
      .orDie
      .map(_.toModel)
  }

  override def findWalletById(walletId: WalletId): UIO[Option[Wallet]] = {
    WalletSql
      .findByIds(Seq(walletId))
      .transactWithoutContext(xa)
      .orDie
      .map(_.headOption.map(_.toModel))
  }

  override def findWalletBySeed(seedDigest: Array[Byte]): UIO[Option[Wallet]] = {
    WalletSql
      .findBySeed(seedDigest)
      .transactWithoutContext(xa)
      .orDie
      .map(_.headOption.map(_.toModel))
  }

  override def getWallets(walletIds: Seq[WalletId]): UIO[Seq[Wallet]] = {
    walletIds match
      case Nil => ZIO.succeed(Nil)
      case ids =>
        WalletSql
          .findByIds(ids)
          .transactWithoutContext(xa)
          .orDie
          .map(_.map(_.toModel))
  }

  override def listWallet(
      offset: Option[Int],
      limit: Option[Int]
  ): UIO[(Seq[Wallet], RuntimeFlags)] = {
    val countCxnIO = WalletSql.lookupCount()

    val walletsCxnIO = WalletSql.lookup(
      offset = offset.getOrElse(0),
      limit = limit.getOrElse(1000)
    )

    val effect = for {
      totalCount <- countCxnIO
      rows <- walletsCxnIO.map(_.map(_.toModel))
    } yield (rows, totalCount.toInt)

    effect
      .transactWithoutContext(xa)
      .orDie
  }

  override def countWalletNotification: URIO[WalletAccessContext, Int] = {
    WalletNotificationSql
      .lookupCount()
      .transactWallet(xa)
      .map(_.toInt)
      .orDie
  }

  override def createWalletNotification(
      config: EventNotificationConfig
  ): URIO[WalletAccessContext, Unit] = {
    WalletNotificationSql
      .insert(db.WalletNotification.from(config))
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

  override def walletNotification: URIO[WalletAccessContext, Seq[EventNotificationConfig]] = {
    WalletNotificationSql
      .lookup()
      .transactWallet(xa)
      .map(_.map(_.toModel))
      .orDie
  }

  override def deleteWalletNotification(id: UUID): URIO[WalletAccessContext, Unit] = {
    WalletNotificationSql
      .delete(id)
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

}

object JdbcWalletNonSecretStorage {

  val layer: URLayer[Transactor[ContextAwareTask], WalletNonSecretStorage] =
    ZLayer.fromFunction(new JdbcWalletNonSecretStorage(_))

}
