package org.hyperledger.identus.agent.walletapi.vault

import org.hyperledger.identus.agent.walletapi.model.WalletSeed
import org.hyperledger.identus.agent.walletapi.storage.WalletSecretStorage
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

class VaultWalletSecretStorage(vaultKV: VaultKVClient) extends WalletSecretStorage {

  override def setWalletSeed(seed: WalletSeed): URIO[WalletAccessContext, Unit] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      path = walletSeedPath(walletId)
      alreadyExist <- vaultKV.get[WalletSeed](path).map(_.isDefined).orDie
      _ <- vaultKV
        .set[WalletSeed](path, seed)
        .when(!alreadyExist)
        .someOrFail(Exception(s"Secret on path $path alraedy exists."))
        .orDie
    } yield ()
  }

  override def findWalletSeed: URIO[WalletAccessContext, Option[WalletSeed]] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      path = walletSeedPath(walletId)
      seed <- vaultKV.get[WalletSeed](path).orDie
    } yield seed
  }

  private def walletSeedPath(walletId: WalletId): String = {
    s"secret/${walletId.toUUID}/seed"
  }

}

object VaultWalletSecretStorage {
  def layer: URLayer[VaultKVClient, WalletSecretStorage] = ZLayer.fromFunction(VaultWalletSecretStorage(_))
}
