package org.hyperledger.identus.wallet.service

import org.hyperledger.identus.wallet.model.*
import org.hyperledger.identus.wallet.model.error.*
import org.hyperledger.identus.wallet.storage.DIDNonSecretStorage
import org.hyperledger.identus.did.core.model.did.*
import org.hyperledger.identus.didcomm.model.*
import org.hyperledger.identus.didcomm.PeerDID
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Secp256k1KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

/** A wrapper around Castor's DIDService providing key-management capability. Analogous to the secretAPI in
  * indy-wallet-sdk.
  */
trait ManagedDIDService {

  private[wallet] def nonSecretStorage: DIDNonSecretStorage

  protected def getDefaultDidDocumentServices: Set[Service] = Set.empty

  def syncManagedDIDState: ZIO[WalletAccessContext, GetManagedDIDError, Unit]

  def syncUnconfirmedUpdateOperations: ZIO[WalletAccessContext, GetManagedDIDError, Unit]

  def findDIDKeyPair(
      did: CanonicalPrismDID,
      keyId: KeyId
  ): URIO[WalletAccessContext, Option[Secp256k1KeyPair | Ed25519KeyPair | X25519KeyPair]]

  def getManagedDIDState(did: CanonicalPrismDID): ZIO[WalletAccessContext, GetManagedDIDError, Option[ManagedDIDState]]

  /** @return A tuple containing a list of items and a count of total items */
  def listManagedDIDPage(
      offset: Int,
      limit: Int
  ): ZIO[WalletAccessContext, GetManagedDIDError, (Seq[ManagedDIDDetail], Int)]

  def publishStoredDID(
      did: CanonicalPrismDID
  ): ZIO[WalletAccessContext, PublishManagedDIDError, ScheduleDIDOperationOutcome]

  def createAndStoreDID(
      didTemplate: ManagedDIDTemplate
  ): ZIO[WalletAccessContext, CreateManagedDIDError, LongFormPrismDID]

  def updateManagedDID(
      did: CanonicalPrismDID,
      actions: Seq[UpdateManagedDIDAction]
  ): ZIO[WalletAccessContext, UpdateManagedDIDError, ScheduleDIDOperationOutcome]

  def deactivateManagedDID(
      did: CanonicalPrismDID
  ): ZIO[WalletAccessContext, UpdateManagedDIDError, ScheduleDIDOperationOutcome]

  /** Returns true when the DID is marked as deactivated on ledger/resolution metadata. */
  def isDidDeactivated(
      did: CanonicalPrismDID
  ): ZIO[WalletAccessContext, GetManagedDIDError, Boolean]

  /** PeerDID related methods */
  def createAndStorePeerDID(
      serviceEndpoint: java.net.URL
  ): URIO[WalletAccessContext, PeerDID]

  def getPeerDID(
      didId: DidId
  ): ZIO[WalletAccessContext, DIDSecretStorageError.KeyNotFoundError, PeerDID]

}

object ManagedDIDService {
  val DEFAULT_MASTER_KEY_ID: String = "master0"
  val reservedKeyIds: Set[String] = Set(DEFAULT_MASTER_KEY_ID)
}
