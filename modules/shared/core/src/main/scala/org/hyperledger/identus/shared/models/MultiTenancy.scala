package org.hyperledger.identus.shared.models

import java.util.UUID
import scala.language.implicitConversions

opaque type WalletId = UUID

object WalletId {
  def fromUUID(uuid: UUID): WalletId = uuid

  def fromUUIDString(uuidStr: String): WalletId = UUID.fromString(uuidStr)
  def random: WalletId = fromUUID(UUID.randomUUID())

  def default: WalletId = fromUUID(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  extension (id: WalletId) { def toUUID: UUID = id }
}

final case class WalletAccessContext(walletId: WalletId)

// This might eventually be unified with WalletAccessContext and introduce some scope / role.
// For now this is only intended for wallet admin related operations.
sealed trait WalletAdministrationContext {
  def isAuthorized(walletId: WalletId): Boolean
}

object WalletAdministrationContext {
  final case class Admin() extends WalletAdministrationContext {
    def isAuthorized(walletId: WalletId): Boolean = true
  }
  final case class SelfService(permittedWallets: Seq[WalletId]) extends WalletAdministrationContext {
    def isAuthorized(walletId: WalletId): Boolean = permittedWallets.contains(walletId)
  }
}
