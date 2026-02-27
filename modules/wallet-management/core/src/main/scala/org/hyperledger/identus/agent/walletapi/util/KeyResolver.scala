package org.hyperledger.identus.agent.walletapi.util

import org.hyperledger.identus.agent.walletapi.model.{
  ManagedDIDHdKeyPath,
  ManagedDIDKeyMeta,
  ManagedDIDRandKeyMeta,
  WalletSeed
}
import org.hyperledger.identus.agent.walletapi.storage.{DIDNonSecretStorage, DIDSecretStorage, WalletSecretStorage}
import org.hyperledger.identus.castor.core.model.did.{EllipticCurve, PrismDID}
import org.hyperledger.identus.shared.crypto.{Apollo, Ed25519KeyPair, Secp256k1KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

class KeyResolver(
    apollo: Apollo,
    nonSecretStorage: DIDNonSecretStorage,
    secretStorage: DIDSecretStorage,
    walletSecretStorage: WalletSecretStorage
) {
  def getKey(
      did: PrismDID,
      keyId: KeyId,
  ): RIO[WalletAccessContext, Option[Secp256k1KeyPair | Ed25519KeyPair | X25519KeyPair]] =
    nonSecretStorage.getKeyMeta(did, keyId).flatMap {
      case None                                   => ZIO.none
      case Some(ManagedDIDKeyMeta.HD(path), oh)   => deriveHdKey(path)
      case Some(ManagedDIDKeyMeta.Rand(meta), oh) => getRandKey(did, keyId, meta, oh)
    }

  private def deriveHdKey(path: ManagedDIDHdKeyPath): RIO[WalletAccessContext, Option[Secp256k1KeyPair]] =
    walletSecretStorage.findWalletSeed.flatMap {
      case None       => ZIO.none
      case Some(seed) => apollo.secp256k1.deriveKeyPair(seed.toByteArray)(path.derivationPath*).asSome
    }

  private def getRandKey(
      did: PrismDID,
      keyId: KeyId,
      meta: ManagedDIDRandKeyMeta,
      operationHash: Array[Byte]
  ): RIO[WalletAccessContext, Option[Ed25519KeyPair | X25519KeyPair]] = {
    meta.curve match {
      case EllipticCurve.SECP256K1 =>
        ZIO.die(Exception("Reading secp256k1 random key is not yet supported"))
      case EllipticCurve.ED25519 =>
        secretStorage.getPrismDIDKeyPair[Ed25519KeyPair](did, keyId, operationHash)
      case EllipticCurve.X25519 =>
        secretStorage.getPrismDIDKeyPair[X25519KeyPair](did, keyId, operationHash)
    }
  }
}
