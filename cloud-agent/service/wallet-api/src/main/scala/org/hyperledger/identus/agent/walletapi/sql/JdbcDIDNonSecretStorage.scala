package org.hyperledger.identus.agent.walletapi.sql

import cats.implicits.toFunctorOps
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.{*, given}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*
import zio.interop.catz.*

import java.time.Instant
import java.util.Objects

class JdbcDIDNonSecretStorage(xa: Transactor[ContextAwareTask], xb: Transactor[Task]) extends DIDNonSecretStorage {

  override def getManagedDIDState(did: PrismDID): RIO[WalletAccessContext, Option[ManagedDIDState]] = {
    val cxnIO =
      sql"""
        | SELECT
        |   did,
        |   publication_status,
        |   atala_operation_content,
        |   publish_operation_id,
        |   created_at,
        |   updated_at,
        |   did_index,
        |   wallet_id
        | FROM public.prism_did_wallet_state
        | WHERE did = $did
        """.stripMargin
        .query[DIDStateRow]
        .option

    cxnIO
      .transactWallet(xa)
      .flatMap(_.map(_.toDomain).fold(ZIO.none)(t => ZIO.fromTry(t).asSome))
  }

  override def insertManagedDID(
      did: PrismDID,
      state: ManagedDIDState,
      hdKey: Map[String, ManagedDIDHdKeyPath],
      randKey: Map[String, ManagedDIDRandKeyMeta]
  ): RIO[WalletAccessContext, Unit] = {
    val insertStateIO = (row: DIDStateRow) => sql"""
        | INSERT INTO public.prism_did_wallet_state(
        |   did,
        |   publication_status,
        |   atala_operation_content,
        |   publish_operation_id,
        |   created_at,
        |   updated_at,
        |   did_index,
        |   wallet_id
        | )
        | VALUES (
        |   ${row.did},
        |   ${row.publicationStatus},
        |   ${row.atalaOperationContent},
        |   ${row.publishOperationId},
        |   ${row.createdAt},
        |   ${row.updatedAt},
        |   ${row.didIndex},
        |   ${row.walletId}
        | )
        """.stripMargin.update

    val operationHash = state.createOperation.toAtalaOperationHash
    val hdKeyValues = (now: Instant) =>
      hdKey.toList.map { case (keyId, path) =>
        (did, keyId, path.keyUsage, Some(path.keyIndex), now, operationHash, path.keyMode, path.curve)
      }
    val randKeyValues = (now: Instant) =>
      randKey.toList.map { (keyId, rand) =>
        (did, keyId, rand.keyUsage, None, now, operationHash, rand.keyMode, rand.curve)
      }
    val insertHdKeyIO =
      Update[
        (
            PrismDID,
            String,
            VerificationRelationship | InternalKeyPurpose,
            Option[Int],
            Instant,
            Array[Byte],
            KeyManagementMode,
            EllipticCurve
        )
      ](
        "INSERT INTO public.prism_did_key(did, key_id, key_usage, key_index, created_at, operation_hash, key_mode, curve_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      )

    val txnIO = (now: Instant, walletId: WalletId) =>
      for {
        _ <- insertStateIO(DIDStateRow.from(did, state, now, walletId)).run
        _ <- insertHdKeyIO.updateMany(hdKeyValues(now))
        _ <- insertHdKeyIO.updateMany(randKeyValues(now))
      } yield ()

    (for {
      walletCtx <- ZIO.service[WalletAccessContext]
      now <- Clock.instant
      _ <- txnIO(now, walletCtx.walletId).transactWallet(xa)
    } yield ()).orDie
  }

  override def updateManagedDID(did: PrismDID, patch: ManagedDIDStatePatch): RIO[WalletAccessContext, Unit] = {
    val status = PublicationStatusType.from(patch.publicationState)
    val publishedOperationId = patch.publicationState match {
      case PublicationState.Created()                       => None
      case PublicationState.PublicationPending(operationId) => Some(operationId)
      case PublicationState.Published(operationId)          => Some(operationId)
    }
    val cxnIO = (now: Instant) => sql"""
           | UPDATE public.prism_did_wallet_state
           | SET
           |   publication_status = $status,
           |   publish_operation_id = $publishedOperationId,
           |   updated_at = $now
           | WHERE did = $did
           """.stripMargin.update

    for {
      now <- Clock.instant
      _ <- cxnIO(now).run.transactWallet(xa)
    } yield ()
  }

  override def getMaxDIDIndex(): RIO[WalletAccessContext, Option[Int]] = {
    val cxnIO =
      sql"""
           | SELECT MAX(did_index)
           | FROM public.prism_did_wallet_state
           | WHERE did_index IS NOT NULL
           """.stripMargin
        .query[Option[Int]]
        .option

    cxnIO.transactWallet(xa).map(_.flatten)
  }

  override def incrementAndGetNextDIDIndex: URIO[WalletAccessContext, Int] = {
    def acquireAdvisoryLock(walletId: WalletId): ConnectionIO[Unit] = {
      // Should be specific to this process
      val PROCESS_UNIQUE_ID = 465263
      val hashCode = Objects.hash(walletId.hashCode(), PROCESS_UNIQUE_ID)
      sql"SELECT pg_advisory_xact_lock($hashCode)".query[Unit].unique.void
    }

    def insertWalletDIDIndexIfNotExists(walletId: WalletId): ConnectionIO[Int] = {
      sql"""
           | INSERT INTO public.last_did_index_per_wallet (wallet_id, last_used_index)
           | VALUES ($walletId, -1)
           | ON CONFLICT (wallet_id) DO NOTHING""".stripMargin.update.run
    }

    def incrementWalletDIDIndex(walletId: WalletId): ConnectionIO[Int] = {
      sql"""
           | UPDATE public.last_did_index_per_wallet
           | SET last_used_index = last_used_index + 1
           | WHERE wallet_id = $walletId
           | RETURNING last_used_index""".stripMargin.query[Int].unique
    }

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      walletId = walletCtx.walletId
      cnxIO = for {
        _ <- acquireAdvisoryLock(walletId)
        _ <- insertWalletDIDIndexIfNotExists(walletId)
        index <- incrementWalletDIDIndex(walletId)
      } yield index
      index <- cnxIO.transactWallet(xa).orDie
    } yield index
  }

  override def getHdKeyCounter(did: PrismDID): RIO[WalletAccessContext, Option[HdKeyIndexCounter]] = {
    val status: ScheduledDIDOperationStatus = ScheduledDIDOperationStatus.Confirmed
    val cxnIO =
      sql"""
           | SELECT
           |   hd.key_usage AS key_usage,
           |   MAX(hd.key_index) AS key_index
           | FROM public.prism_did_key hd
           |   LEFT JOIN public.prism_did_wallet_state ws ON hd.did = ws.did
           |   LEFT JOIN public.prism_did_update_lineage ul ON hd.operation_hash = ul.operation_hash
           | WHERE
           |   hd.did = $did
           |   AND hd.key_mode = ${KeyManagementMode.HD}
           |   AND (ul.status = $status OR (ul.status IS NULL AND hd.operation_hash = sha256(ws.atala_operation_content)))
           | GROUP BY hd.did, hd.key_usage
           """.stripMargin
        .query[(VerificationRelationship | InternalKeyPurpose, Int)]
        .to[List]

    getManagedDIDState(did)
      .map(_.map(_.didIndex))
      .flatMap {
        case None => ZIO.none
        case Some(didIndex) =>
          for {
            keyUsageIndex <- cxnIO.transactWallet(xa)
            keyUsageIndexMap = keyUsageIndex.map { case (k, v) => k -> (v + 1) }.toMap
          } yield Some(
            HdKeyIndexCounter(
              didIndex,
              VerificationRelationshipCounter(
                authentication = keyUsageIndexMap.getOrElse(VerificationRelationship.Authentication, 0),
                assertionMethod = keyUsageIndexMap.getOrElse(VerificationRelationship.AssertionMethod, 0),
                keyAgreement = keyUsageIndexMap.getOrElse(VerificationRelationship.KeyAgreement, 0),
                capabilityInvocation = keyUsageIndexMap.getOrElse(VerificationRelationship.CapabilityInvocation, 0),
                capabilityDelegation = keyUsageIndexMap.getOrElse(VerificationRelationship.CapabilityDelegation, 0),
              ),
              InternalKeyCounter(
                master = keyUsageIndexMap.getOrElse(InternalKeyPurpose.Master, 0),
                revocation = keyUsageIndexMap.getOrElse(InternalKeyPurpose.Revocation, 0),
              )
            )
          )
      }
  }

  override def getKeyMeta(
      did: PrismDID,
      keyId: KeyId
  ): RIO[WalletAccessContext, Option[(ManagedDIDKeyMeta, Array[Byte])]] = {
    val status: ScheduledDIDOperationStatus = ScheduledDIDOperationStatus.Confirmed
    val cxnIO =
      sql"""
           | SELECT
           |   ws.did_index,
           |   hd.key_usage,
           |   hd.key_index,
           |   hd.key_mode,
           |   hd.curve_name,
           |   hd.operation_hash
           | FROM public.prism_did_key hd
           |   LEFT JOIN public.prism_did_wallet_state ws ON hd.did = ws.did
           |   LEFT JOIN public.prism_did_update_lineage ul ON hd.operation_hash = ul.operation_hash
           | WHERE
           |   hd.did = $did
           |   AND hd.key_id = ${keyId.value}
           |   AND (ul.status = $status OR (ul.status IS NULL AND hd.operation_hash = sha256(ws.atala_operation_content)))
           """.stripMargin
        .query[
          (
              Option[Int],
              VerificationRelationship | InternalKeyPurpose,
              Option[Int],
              KeyManagementMode,
              EllipticCurve,
              Array[Byte]
          )
        ]
        .option

    cxnIO
      .transactWallet(xa)
      .flatMap {
        case Some(Some(didIndex), keyUsage, Some(keyIndex), KeyManagementMode.HD, _, oh) =>
          ZIO.some(ManagedDIDKeyMeta.HD(ManagedDIDHdKeyPath(didIndex, keyUsage, keyIndex)), oh)
        case Some(_, keyUsage, _, KeyManagementMode.RANDOM, curve, oh) =>
          ZIO.some(ManagedDIDKeyMeta.Rand(ManagedDIDRandKeyMeta(keyUsage, curve)) -> oh)
        case None => ZIO.none
        case _    => ZIO.dieMessage(s"Key metadata of $did#$keyId is corrupted")
      }
  }

  override def insertKeyMeta(
      did: PrismDID,
      keyId: KeyId,
      meta: ManagedDIDKeyMeta,
      operationHash: Array[Byte]
  ): RIO[WalletAccessContext, Unit] = {
    val (keyUsage, keyIndex, keyMode, curve) = meta match {
      case ManagedDIDKeyMeta.HD(k)   => (k.keyUsage, Some(k.keyIndex), k.keyMode, k.curve)
      case ManagedDIDKeyMeta.Rand(k) => (k.keyUsage, None, k.keyMode, k.curve)
    }
    val cxnIO = (now: Instant) =>
      sql"""
          | INSERT INTO public.prism_did_key(did, key_id, key_usage, key_index, created_at, operation_hash, key_mode, curve_name)
          | VALUES
          | (
          |  $did,
          |  ${keyId.value},
          |  ${keyUsage},
          |  ${keyIndex},
          |  $now,
          |  $operationHash,
          |  ${keyMode},
          |  ${curve}
          | )
          |""".stripMargin.update

    for {
      now <- Clock.instant
      _ <- cxnIO(now).run.transactWallet(xa)
    } yield ()
  }

  override def listManagedDID(
      offset: Option[Int],
      limit: Option[Int]
  ): RIO[WalletAccessContext, (Seq[(PrismDID, ManagedDIDState)], Int)] = {
    val countCxnIO =
      sql"""
        | SELECT COUNT(*)
        | FROM public.prism_did_wallet_state
        """.stripMargin
        .query[Int]
        .unique

    val baseFr =
      sql"""
           | SELECT
           |   did,
           |   publication_status,
           |   atala_operation_content,
           |   publish_operation_id,
           |   created_at,
           |   updated_at,
           |   did_index,
           |   wallet_id
           | FROM public.prism_did_wallet_state
           | ORDER BY created_at
           """.stripMargin
    val withOffsetFr = offset.fold(baseFr)(offsetValue => baseFr ++ fr"OFFSET $offsetValue")
    val withOffsetAndLimitFr = limit.fold(withOffsetFr)(limitValue => withOffsetFr ++ fr"LIMIT $limitValue")
    val didsCxnIO =
      withOffsetAndLimitFr
        .query[DIDStateRow]
        .to[List]

    val effect = for {
      totalCount <- countCxnIO
      rows <- didsCxnIO
    } yield (rows, totalCount)

    effect
      .transactWallet(xa)
      .flatMap { case (rows, totalCount) =>
        val results = rows.map(row => row.toDomain.map(row.did -> _))
        ZIO.foreach(results)(ZIO.fromTry).map(_ -> totalCount)
      }
  }

  override def insertDIDUpdateLineage(
      did: PrismDID,
      updateLineage: DIDUpdateLineage
  ): RIO[WalletAccessContext, Unit] = {
    val cxnIO =
      sql"""
           | INSERT INTO public.prism_did_update_lineage(
           |   did,
           |   operation_hash,
           |   previous_operation_hash,
           |   status,
           |   operation_id,
           |   created_at,
           |   updated_at
           | )
           | VALUES (
           |   $did,
           |   ${updateLineage.operationHash},
           |   ${updateLineage.previousOperationHash},
           |   ${updateLineage.status},
           |   ${updateLineage.operationId},
           |   ${updateLineage.createdAt},
           |   ${updateLineage.updatedAt}
           | )
           """.stripMargin.update

    cxnIO.run.transactWallet(xa).unit
  }

  override def listUpdateLineage(
      did: Option[PrismDID],
      status: Option[ScheduledDIDOperationStatus]
  ): RIO[WalletAccessContext, Seq[DIDUpdateLineage]] = {
    val didFilter = did.map(d => fr"did = $d")
    val statusFilter = status.map(s => fr"status = $s")
    val whereFr = Fragments.whereAndOpt(didFilter, statusFilter)
    val baseFr =
      sql"""
           | SELECT
           |   operation_id,
           |   operation_hash,
           |   previous_operation_hash,
           |   status,
           |   created_at,
           |   updated_at
           | FROM public.prism_did_update_lineage
           """.stripMargin
    val cxnIO = (baseFr ++ whereFr)
      .query[DIDUpdateLineage]
      .to[List]

    cxnIO.transactWallet(xa)
  }

  override def setDIDUpdateLineageStatus(
      operationId: Array[Byte],
      status: ScheduledDIDOperationStatus
  ): RIO[WalletAccessContext, Unit] = {
    val cxnIO = (now: Instant) => sql"""
            | UPDATE public.prism_did_update_lineage
            | SET
            |   status = $status,
            |   updated_at = $now
            | WHERE operation_id = $operationId
            """.stripMargin.update

    Clock.instant.flatMap(now => cxnIO(now).run.transactWallet(xa)).unit
  }

  override def createPeerDIDRecord(did: DidId): RIO[WalletAccessContext, Int] = {
    val cxnIO =
      sql"""
           | INSERT INTO public.peer_did(
           |  did,
           |  created_at,
           |  wallet_id
           | ) VALUES (
           |  ${did},
           |  ${Instant.now},
           |  current_setting('app.current_wallet_id')::UUID
           | )
            """.stripMargin.update

    cxnIO.run.transactWallet(xa)
  }

  override def getPeerDIDRecord(did: DidId): Task[Option[PeerDIDRecord]] = {
    val cnxIO =
      sql"""
           | SELECT
           |  did,
           |  created_at,
           |  wallet_id
           | FROM public.peer_did
           | WHERE
           |  did = $did
            """.stripMargin
        .query[PeerDIDRecord]
        .option

    cnxIO.transact(xb)
  }

  override def getPrismDidWalletId(prismDid: PrismDID): Task[Option[WalletId]] = {
    val cnxIO = sql"""
           | SELECT
           |  wallet_id
           | FROM public.prism_did_wallet_state
           | WHERE
           |  did = ${prismDid}
            """.stripMargin
      .query[WalletId]
      .option

    cnxIO.transact(xb)
  }

}

object JdbcDIDNonSecretStorage {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], DIDNonSecretStorage] =
    ZLayer.fromFunction(new JdbcDIDNonSecretStorage(_, _))
}
