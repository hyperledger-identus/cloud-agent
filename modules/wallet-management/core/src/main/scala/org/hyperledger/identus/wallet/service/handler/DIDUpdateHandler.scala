package org.hyperledger.identus.wallet.service.handler

import org.hyperledger.identus.wallet.model.{
  DIDUpdateLineage,
  ManagedDIDKeyMeta,
  ManagedDIDState,
  UpdateDIDKey,
  UpdateManagedDIDAction,
  WalletSeed
}
import org.hyperledger.identus.wallet.model.error.*
import org.hyperledger.identus.wallet.model.error.given
import org.hyperledger.identus.wallet.storage.{DIDNonSecretStorage, DIDSecretStorage, WalletSecretStorage}
import org.hyperledger.identus.wallet.util.OperationFactory
import org.hyperledger.identus.did.core.model.did.{
  PrismDIDOperation,
  ScheduledDIDOperationStatus,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.did.core.model.did.PrismDIDOperation.Update
import org.hyperledger.identus.shared.crypto.{Apollo, Ed25519KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

import scala.collection.immutable.ArraySeq

private[wallet] class DIDUpdateHandler(
    apollo: Apollo,
    nonSecretStorage: DIDNonSecretStorage,
    secretStorage: DIDSecretStorage,
    walletSecretStorage: WalletSecretStorage,
    publicationHandler: PublicationHandler
) {
  def materialize(
      state: ManagedDIDState,
      previousOperationHash: Array[Byte],
      actions: Seq[UpdateManagedDIDAction]
  ): ZIO[WalletAccessContext, UpdateManagedDIDError, DIDUpdateMaterial] = {
    val operationFactory = OperationFactory(apollo)
    val did = state.createOperation.did
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      seed <- walletSecretStorage.findWalletSeed
        .someOrElseZIO(ZIO.dieMessage(s"Wallet seed for wallet $walletId does not exist"))
      keyCounter <- nonSecretStorage
        .getHdKeyCounter(did)
        .mapError(UpdateManagedDIDError.WalletStorageError.apply)
        .someOrFail(
          UpdateManagedDIDError.DataIntegrityError("DID is in HD key mode, but its key counter is not found")
        )
      result <- operationFactory.makeUpdateOperation(seed.toByteArray)(
        did,
        previousOperationHash,
        actions,
        keyCounter
      )
      (operation, hdKey) = result
      signedOperation <- publicationHandler.signOperationWithMasterKey[UpdateManagedDIDError](state, operation)
    } yield HdKeyUpdateMaterial(nonSecretStorage, secretStorage)(operation, signedOperation, state, hdKey)
  }
}

private[wallet] trait DIDUpdateMaterial {

  def operation: PrismDIDOperation.Update

  def signedOperation: SignedPrismDIDOperation

  def state: ManagedDIDState

  def persist: RIO[WalletAccessContext, Unit]

  protected final def persistUpdateLineage(nonSecretStorage: DIDNonSecretStorage): RIO[WalletAccessContext, Unit] = {
    val did = operation.did
    for {
      updateLineage <- Clock.instant.map { now =>
        DIDUpdateLineage(
          operationId = ArraySeq.from(signedOperation.toAtalaOperationId),
          operationHash = ArraySeq.from(operation.toAtalaOperationHash),
          previousOperationHash = operation.previousOperationHash,
          status = ScheduledDIDOperationStatus.Pending,
          createdAt = now,
          updatedAt = now
        )
      }
      _ <- nonSecretStorage.insertDIDUpdateLineage(did, updateLineage)
    } yield ()
  }

}

private class HdKeyUpdateMaterial(nonSecretStorage: DIDNonSecretStorage, secretStorage: DIDSecretStorage)(
    val operation: PrismDIDOperation.Update,
    val signedOperation: SignedPrismDIDOperation,
    val state: ManagedDIDState,
    keys: UpdateDIDKey
) extends DIDUpdateMaterial {

  private def persistKeyMaterial: RIO[WalletAccessContext, Unit] = {
    val did = operation.did
    val operationHash = operation.toAtalaOperationHash
    // OPTIMIZE: refactoring insertKey to a bulk insert (non-secret part)
    for {
      _ <- ZIO.foreach(keys.hdKeys.toList) { case (keyId, keyPath) =>
        val meta = ManagedDIDKeyMeta.HD(keyPath)
        nonSecretStorage.insertKeyMeta(did, KeyId(keyId), meta, operationHash)
      }
      _ <- ZIO.foreach(keys.internalKeys.toList) { case (keyId, keyPath) =>
        val meta = ManagedDIDKeyMeta.HD(keyPath)
        nonSecretStorage.insertKeyMeta(did, KeyId(keyId), meta, operationHash)
      }
      _ <- ZIO.foreach(keys.randKeyMeta.toList) { case (keyId, rand) =>
        val meta = ManagedDIDKeyMeta.Rand(rand)
        nonSecretStorage.insertKeyMeta(did, KeyId(keyId), meta, operationHash)
      }
      _ <- ZIO.foreach(keys.randKeys.toList) { case (keyId, key) =>
        key.keyPair match {
          case kp: Ed25519KeyPair => secretStorage.insertPrismDIDKeyPair(did, KeyId(keyId), operationHash, kp)
          case kp: X25519KeyPair  => secretStorage.insertPrismDIDKeyPair(did, KeyId(keyId), operationHash, kp)
        }
      }
    } yield ()

  }

  override def persist: RIO[WalletAccessContext, Unit] =
    for {
      _ <- persistKeyMaterial
      _ <- persistUpdateLineage(nonSecretStorage)
    } yield ()

}
