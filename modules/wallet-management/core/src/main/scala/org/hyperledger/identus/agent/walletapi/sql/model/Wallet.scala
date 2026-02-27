package org.hyperledger.identus.agent.walletapi.sql.model

import io.getquill.*
import io.getquill.context.json.PostgresJsonExtensions
import io.getquill.doobie.DoobieContext
import org.hyperledger.identus.agent.walletapi.model
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.models.WalletId

import java.net.URL
import java.time.Instant
import java.util.UUID

final case class Wallet(
    walletId: WalletId,
    name: String,
    createdAt: Instant,
    updatedAt: Instant,
    seedDigest: Array[Byte]
)

object Wallet {
  def from(wallet: model.Wallet, seedDigest: Array[Byte]): Wallet = {
    Wallet(
      walletId = wallet.id,
      name = wallet.name,
      createdAt = wallet.createdAt,
      updatedAt = wallet.updatedAt,
      seedDigest = seedDigest
    )
  }

  extension (wallet: Wallet) {
    def toModel: model.Wallet =
      model.Wallet(
        id = wallet.walletId,
        name = wallet.name,
        createdAt = wallet.createdAt,
        updatedAt = wallet.updatedAt
      )
  }
}

object WalletSql extends DoobieContext.Postgres(SnakeCase) {
  def insert(wallet: Wallet) = run {
    quote(
      query[Wallet]
        .insertValue(lift(wallet))
    ).returning(w => w)
  }

  def findByIds(walletIds: Seq[WalletId]) = run {
    quote(query[Wallet].filter(p => liftQuery(walletIds.map(_.toUUID)).contains(p.walletId)))
  }

  def findBySeed(seedDigest: Array[Byte]) = run {
    quote(query[Wallet].filter(_.seedDigest == lift(seedDigest)).take(1))
  }

  def lookupCount() = run { quote(query[Wallet].size) }

  def lookup(offset: Int, limit: Int) = run {
    quote(query[Wallet].drop(lift(offset)).take(lift(limit)))
  }
}

final case class WalletNotification(
    id: UUID,
    walletId: WalletId,
    url: URL,
    customHeaders: JsonValue[Map[String, String]],
    createdAt: Instant,
)

object WalletNotification {
  def from(notification: EventNotificationConfig): WalletNotification = {
    WalletNotification(
      id = notification.id,
      walletId = notification.walletId,
      url = notification.url,
      customHeaders = JsonValue(notification.customHeaders),
      createdAt = notification.createdAt,
    )
  }

  extension (notification: WalletNotification) {
    def toModel: EventNotificationConfig =
      EventNotificationConfig(
        id = notification.id,
        walletId = notification.walletId,
        url = notification.url,
        customHeaders = notification.customHeaders.value,
        createdAt = notification.createdAt,
      )
  }
}

object WalletNotificationSql extends DoobieContext.Postgres(SnakeCase), PostgresJsonExtensions {
  def insert(notification: WalletNotification) = run {
    quote(
      query[WalletNotification]
        .insertValue(lift(notification))
    )
  }

  def lookupCount() = run {
    quote(query[WalletNotification].size)
  }

  def lookup() = run {
    quote(query[WalletNotification])
  }

  def delete(id: UUID) = run {
    quote(query[WalletNotification].filter(_.id == lift(id)).delete)
  }
}
