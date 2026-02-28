package org.hyperledger.identus.notifications

import org.hyperledger.identus.shared.models.WalletId

import java.time.Instant
import java.util.UUID

case class Event[A](`type`: String, id: UUID, ts: Instant, data: A, walletId: WalletId)

object Event {
  def apply[A](`type`: String, data: A, walletId: WalletId): Event[A] =
    Event(`type`, UUID.randomUUID(), Instant.now(), data, walletId)
}
