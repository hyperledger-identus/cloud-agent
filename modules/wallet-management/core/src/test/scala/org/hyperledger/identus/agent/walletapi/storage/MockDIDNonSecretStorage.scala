package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.castor.core.model.did.{PrismDID, ScheduledDIDOperationStatus}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*
import zio.mock.{Expectation, Mock, Proxy}
import zio.test.Assertion.equalTo

import scala.collection.immutable.ArraySeq

case class MockDIDNonSecretStorage(proxy: Proxy) extends DIDNonSecretStorage {
  override def insertDIDUpdateLineage(did: PrismDID, updateLineage: DIDUpdateLineage): RIO[WalletAccessContext, Unit] =
    proxy(MockDIDNonSecretStorage.InsertDIDUpdateLineage, (did, updateLineage))

  override def listUpdateLineage(
      did: Option[PrismDID],
      status: Option[ScheduledDIDOperationStatus]
  ): RIO[WalletAccessContext, Seq[DIDUpdateLineage]] = proxy(MockDIDNonSecretStorage.ListUpdateLineage, (did, status))

  override def setDIDUpdateLineageStatus(
      operationId: Array[Byte],
      status: ScheduledDIDOperationStatus
  ): RIO[WalletAccessContext, Unit] = proxy(MockDIDNonSecretStorage.SetDIDUpdateLineageStatus, (operationId, status))

  override def createPeerDIDRecord(did: DidId): RIO[WalletAccessContext, Int] =
    proxy(MockDIDNonSecretStorage.CreatePeerDIDRecord, did)

  override def getPeerDIDRecord(did: DidId): Task[Option[PeerDIDRecord]] =
    proxy(MockDIDNonSecretStorage.GetPeerDIDRecord, did)

  override def getPrismDidWalletId(prismDid: PrismDID): Task[Option[WalletId]] =
    proxy(MockDIDNonSecretStorage.GetPrismDidWalletId, prismDid)

  override def getManagedDIDState(did: PrismDID): RIO[WalletAccessContext, Option[ManagedDIDState]] =
    proxy(MockDIDNonSecretStorage.GetManagedDIDState, did)

  override def insertManagedDID(
      did: PrismDID,
      state: ManagedDIDState,
      hdKey: Map[String, ManagedDIDHdKeyPath],
      randKey: Map[String, ManagedDIDRandKeyMeta]
  ): RIO[WalletAccessContext, Unit] = proxy(MockDIDNonSecretStorage.InsertManagedDID, (did, state, hdKey, randKey))

  override def updateManagedDID(did: PrismDID, patch: ManagedDIDStatePatch): RIO[WalletAccessContext, Unit] =
    proxy(MockDIDNonSecretStorage.UpdateManagedDID, (did, patch))

  override def getMaxDIDIndex(): RIO[WalletAccessContext, Option[Int]] =
    proxy(MockDIDNonSecretStorage.GetMaxDIDIndex)

  override def incrementAndGetNextDIDIndex: URIO[WalletAccessContext, RuntimeFlags] =
    proxy(MockDIDNonSecretStorage.IncrementAndGetNextDIDIndex)

  override def getHdKeyCounter(did: PrismDID): RIO[WalletAccessContext, Option[HdKeyIndexCounter]] =
    proxy(MockDIDNonSecretStorage.GetHdKeyCounter, did)

  override def getKeyMeta(
      did: PrismDID,
      keyId: KeyId
  ): RIO[WalletAccessContext, Option[(ManagedDIDKeyMeta, Array[Byte])]] =
    proxy(MockDIDNonSecretStorage.GetKeyMeta, (did, keyId))

  override def insertKeyMeta(
      did: PrismDID,
      keyId: KeyId,
      meta: ManagedDIDKeyMeta,
      operationHash: Array[Byte]
  ): RIO[WalletAccessContext, Unit] =
    proxy(MockDIDNonSecretStorage.InsertHdKeyMeta, (did, keyId, meta, operationHash))

  override def listManagedDID(
      offset: Option[Int],
      limit: Option[Int]
  ): RIO[WalletAccessContext, (Seq[(PrismDID, ManagedDIDState)], Int)] =
    proxy(MockDIDNonSecretStorage.ListManagedDID, (offset, limit))
}
object MockDIDNonSecretStorage extends Mock[DIDNonSecretStorage] {
  object InsertDIDUpdateLineage extends Effect[(PrismDID, DIDUpdateLineage), Throwable, Unit]
  object ListUpdateLineage
      extends Effect[(Option[PrismDID], Option[ScheduledDIDOperationStatus]), Throwable, Seq[DIDUpdateLineage]]
  object SetDIDUpdateLineageStatus extends Effect[(Array[Byte], ScheduledDIDOperationStatus), Throwable, Unit]
  object CreatePeerDIDRecord extends Effect[DidId, Throwable, Int]
  object GetPeerDIDRecord extends Effect[DidId, Throwable, Option[PeerDIDRecord]]
  object GetPrismDidWalletId extends Effect[PrismDID, Throwable, Option[WalletId]]
  object GetManagedDIDState extends Effect[PrismDID, Throwable, Option[ManagedDIDState]]
  object InsertManagedDID
      extends Effect[
        (PrismDID, ManagedDIDState, Map[String, ManagedDIDHdKeyPath], Map[String, ManagedDIDRandKeyMeta]),
        Throwable,
        Unit
      ]
  object UpdateManagedDID extends Effect[(PrismDID, ManagedDIDStatePatch), Throwable, Unit]
  object GetMaxDIDIndex extends Effect[Unit, Throwable, Option[Int]]
  object IncrementAndGetNextDIDIndex extends Effect[Unit, Nothing, Int]
  object GetHdKeyCounter extends Effect[PrismDID, Throwable, Option[HdKeyIndexCounter]]
  object GetKeyMeta extends Effect[(PrismDID, KeyId), Throwable, Option[(ManagedDIDKeyMeta, Array[Byte])]]
  object InsertHdKeyMeta extends Effect[(PrismDID, KeyId, ManagedDIDKeyMeta, Array[Byte]), Throwable, Unit]
  object ListHdKeyPath extends Effect[PrismDID, Throwable, Seq[(String, ArraySeq[Byte], ManagedDIDHdKeyPath)]]
  object ListManagedDID extends Effect[(Option[Int], Option[Int]), Throwable, (Seq[(PrismDID, ManagedDIDState)], Int)]

  override val compose: URLayer[mock.Proxy, DIDNonSecretStorage] = ZLayer.fromFunction(MockDIDNonSecretStorage(_))

  def getPrismDidWalletIdExpectation(prismDID: PrismDID, walletId: WalletId): Expectation[DIDNonSecretStorage] =
    MockDIDNonSecretStorage.GetPrismDidWalletId(
      assertion = equalTo(prismDID),
      result = Expectation.value(Some(walletId))
    )
}
