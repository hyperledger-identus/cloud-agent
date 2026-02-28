package org.hyperledger.identus.wallet.storage

import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.wallet.model.WalletSeed
import zio.*

trait WalletSecretStorage {
  def setWalletSeed(seed: WalletSeed): URIO[WalletAccessContext, Unit]
  def findWalletSeed: URIO[WalletAccessContext, Option[WalletSeed]]
}
