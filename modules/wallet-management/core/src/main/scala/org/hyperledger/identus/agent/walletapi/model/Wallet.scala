package org.hyperledger.identus.agent.walletapi.model

import org.hyperledger.identus.shared.models.WalletId

import java.time.temporal.ChronoUnit
import java.time.Instant

final case class Wallet(
    id: WalletId,
    name: String,
    createdAt: Instant,
    updatedAt: Instant
) {
  def withUpdatedAt(updatedAt: Instant): Wallet = copy(updatedAt = updatedAt)
  def withTruncatedTimestamp(unit: ChronoUnit = ChronoUnit.MICROS): Wallet = copy(
    createdAt = createdAt.truncatedTo(unit),
    updatedAt = updatedAt.truncatedTo(unit)
  )
}

object Wallet {
  def apply(name: String, walletId: WalletId): Wallet = {
    val now = Instant.now
    Wallet(
      id = walletId,
      name = name,
      createdAt = now,
      updatedAt = now,
    ).withTruncatedTimestamp()
  }

  def apply(name: String): Wallet = apply(name, WalletId.random).withTruncatedTimestamp()
}
