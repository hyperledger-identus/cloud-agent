package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.castor.core.model.did.{PrismDID, ScheduledDIDOperationStatus}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*

trait DIDNonSecretStorage {

  def getManagedDIDState(did: PrismDID): RIO[WalletAccessContext, Option[ManagedDIDState]]

  def insertManagedDID(
      did: PrismDID,
      state: ManagedDIDState,
      hdKey: Map[String, ManagedDIDHdKeyPath],
      randKey: Map[String, ManagedDIDRandKeyMeta]
  ): RIO[WalletAccessContext, Unit]

  def updateManagedDID(did: PrismDID, patch: ManagedDIDStatePatch): RIO[WalletAccessContext, Unit]

  def getMaxDIDIndex(): RIO[WalletAccessContext, Option[Int]]

  def incrementAndGetNextDIDIndex: URIO[WalletAccessContext, Int]

  def getHdKeyCounter(did: PrismDID): RIO[WalletAccessContext, Option[HdKeyIndexCounter]]

  /** Return a tuple of key metadata and the operation hash */
  def getKeyMeta(did: PrismDID, keyId: KeyId): RIO[WalletAccessContext, Option[(ManagedDIDKeyMeta, Array[Byte])]]

  def insertKeyMeta(
      did: PrismDID,
      keyId: KeyId,
      meta: ManagedDIDKeyMeta,
      operationHash: Array[Byte]
  ): RIO[WalletAccessContext, Unit]

  /** Return a list of Managed DID as well as a count of all filtered items */
  def listManagedDID(
      offset: Option[Int],
      limit: Option[Int]
  ): RIO[WalletAccessContext, (Seq[(PrismDID, ManagedDIDState)], Int)]

  def insertDIDUpdateLineage(did: PrismDID, updateLineage: DIDUpdateLineage): RIO[WalletAccessContext, Unit]

  def listUpdateLineage(
      did: Option[PrismDID],
      status: Option[ScheduledDIDOperationStatus]
  ): RIO[WalletAccessContext, Seq[DIDUpdateLineage]]

  def setDIDUpdateLineageStatus(
      operationId: Array[Byte],
      status: ScheduledDIDOperationStatus
  ): RIO[WalletAccessContext, Unit]

  def createPeerDIDRecord(did: DidId): RIO[WalletAccessContext, Int]

  def getPeerDIDRecord(did: DidId): Task[Option[PeerDIDRecord]]

  def getPrismDidWalletId(prismDid: PrismDID): Task[Option[WalletId]]

}
