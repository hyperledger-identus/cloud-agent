package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.WalletSeed
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

trait WalletSecretStorage {
  def setWalletSeed(seed: WalletSeed): URIO[WalletAccessContext, Unit]
  def findWalletSeed: URIO[WalletAccessContext, Option[WalletSeed]]
}
