package io.iohk.atala.agent.walletapi.sql

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.iohk.atala.agent.walletapi.model.HdKeyIndexCounter
import io.iohk.atala.agent.walletapi.model.InternalKeyCounter
import io.iohk.atala.agent.walletapi.model.ManagedDIDHdKeyPath
import io.iohk.atala.agent.walletapi.model.PublicationState
import io.iohk.atala.agent.walletapi.model.VerificationRelationshipCounter
import io.iohk.atala.agent.walletapi.model.{DIDUpdateLineage, ManagedDIDState, ManagedDIDStatePatch}
import io.iohk.atala.agent.walletapi.storage.DIDNonSecretStorage
import io.iohk.atala.castor.core.model.did.InternalKeyPurpose
import io.iohk.atala.castor.core.model.did.VerificationRelationship
import io.iohk.atala.castor.core.model.did.{PrismDID, ScheduledDIDOperationStatus}
import io.iohk.atala.shared.db.Implicits.*
import io.iohk.atala.shared.db.WalletTask
import io.iohk.atala.shared.models.WalletAccessContext
import io.iohk.atala.shared.models.WalletId
import java.time.Instant
import scala.collection.immutable.ArraySeq
import zio.*
import zio.interop.catz.*

class JdbcDIDNonSecretStorage(xa: Transactor[WalletTask]) extends DIDNonSecretStorage {

  override def getManagedDIDState(did: PrismDID): RIO[WalletAccessContext, Option[ManagedDIDState]] = {
    val cxnIO = (walletId: WalletId) =>
      sql"""
        | SELECT
        |   did,
        |   publication_status,
        |   atala_operation_content,
        |   publish_operation_id,
        |   created_at,
        |   updated_at,
        |   key_mode,
        |   did_index,
        |   wallet_id
        | FROM public.prism_did_wallet_state
        | WHERE did = $did AND wallet_id = $walletId
        """.stripMargin
        .query[DIDStateRow]
        .option

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      result <- cxnIO(walletCtx.walletId)
        .walletTransact(xa)
        .flatMap(_.map(_.toDomain).fold(ZIO.none)(t => ZIO.fromTry(t).asSome))
    } yield result
  }

  override def insertManagedDID(
      did: PrismDID,
      state: ManagedDIDState,
      hdKey: Map[String, ManagedDIDHdKeyPath]
  ): RIO[WalletAccessContext, Unit] = {
    val insertStateIO = (row: DIDStateRow) => sql"""
        | INSERT INTO public.prism_did_wallet_state(
        |   did,
        |   publication_status,
        |   atala_operation_content,
        |   publish_operation_id,
        |   created_at,
        |   updated_at,
        |   key_mode,
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
        |   ${row.keyMode},
        |   ${row.didIndex},
        |   ${row.walletId}
        | )
        """.stripMargin.update

    val operationHash = state.createOperation.toAtalaOperationHash
    val hdKeyValues = (now: Instant) =>
      hdKey.toList.map { case (key, path) => (did, key, path.keyUsage, path.keyIndex, now, operationHash) }
    val insertHdKeyIO =
      Update[(PrismDID, String, VerificationRelationship | InternalKeyPurpose, Int, Instant, Array[Byte])](
        "INSERT INTO public.prism_did_hd_key(did, key_id, key_usage, key_index, created_at, operation_hash) VALUES (?, ?, ?, ?, ?, ?)"
      )

    val txnIO = (now: Instant, walletId: WalletId) =>
      for {
        _ <- insertStateIO(DIDStateRow.from(did, state, now, walletId)).run
        _ <- insertHdKeyIO.updateMany(hdKeyValues(now))
      } yield ()

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      now <- Clock.instant
      _ <- txnIO(now, walletCtx.walletId).walletTransact(xa)
    } yield ()
  }

  override def updateManagedDID(did: PrismDID, patch: ManagedDIDStatePatch): RIO[WalletAccessContext, Unit] = {
    val status = PublicationStatusType.from(patch.publicationState)
    val publishedOperationId = patch.publicationState match {
      case PublicationState.Created()                       => None
      case PublicationState.PublicationPending(operationId) => Some(operationId)
      case PublicationState.Published(operationId)          => Some(operationId)
    }
    val cxnIO = (now: Instant, walletId: WalletId) => sql"""
           | UPDATE public.prism_did_wallet_state
           | SET
           |   publication_status = $status,
           |   publish_operation_id = $publishedOperationId,
           |   updated_at = $now
           | WHERE did = $did AND wallet_id = $walletId
           """.stripMargin.update

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      now <- Clock.instant
      _ <- cxnIO(now, walletCtx.walletId).run.walletTransact(xa)
    } yield ()
  }

  override def getMaxDIDIndex(): RIO[WalletAccessContext, Option[Int]] = {
    val cxnIO = (walletId: WalletId) =>
      sql"""
           | SELECT MAX(did_index)
           | FROM public.prism_did_wallet_state
           | WHERE did_index IS NOT NULL AND wallet_id = $walletId
           """.stripMargin
        .query[Option[Int]]
        .option

    ZIO.serviceWithZIO[WalletAccessContext] { walletCtx =>
      cxnIO(walletCtx.walletId).walletTransact(xa).map(_.flatten)
    }
  }

  override def getHdKeyCounter(did: PrismDID): RIO[WalletAccessContext, Option[HdKeyIndexCounter]] = {
    val status: ScheduledDIDOperationStatus = ScheduledDIDOperationStatus.Confirmed
    val cxnIO =
      sql"""
           | SELECT
           |   hd.key_usage AS key_usage,
           |   MAX(hd.key_index) AS key_index
           | FROM public.prism_did_hd_key hd
           |   LEFT JOIN public.prism_did_wallet_state ws ON hd.did = ws.did
           |   LEFT JOIN public.prism_did_update_lineage ul ON hd.operation_hash = ul.operation_hash
           | WHERE
           |   hd.did = $did
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
            keyUsageIndex <- cxnIO.walletTransact(xa)
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

  override def getHdKeyPath(did: PrismDID, keyId: String): RIO[WalletAccessContext, Option[ManagedDIDHdKeyPath]] = {
    val status: ScheduledDIDOperationStatus = ScheduledDIDOperationStatus.Confirmed
    val cxnIO = (walletId: WalletId) =>
      sql"""
           | SELECT
           |   ws.did_index,
           |   hd.key_usage,
           |   hd.key_index
           | FROM public.prism_did_hd_key hd
           |   LEFT JOIN public.prism_did_wallet_state ws ON hd.did = ws.did
           |   LEFT JOIN public.prism_did_update_lineage ul ON hd.operation_hash = ul.operation_hash
           | WHERE
           |   hd.did = $did
           |   AND hd.key_id = $keyId
           |   AND (ul.status = $status OR (ul.status IS NULL AND hd.operation_hash = sha256(ws.atala_operation_content)))
           |   AND ws.wallet_id = $walletId
           """.stripMargin
        .query[ManagedDIDHdKeyPath]
        .option

    ZIO.serviceWithZIO[WalletAccessContext] { walletCtx =>
      cxnIO(walletCtx.walletId).walletTransact(xa)
    }
  }

  override def listHdKeyPath(
      did: PrismDID
  ): RIO[WalletAccessContext, Seq[(String, ArraySeq[Byte], ManagedDIDHdKeyPath)]] = {
    val cxnIO =
      sql"""
        | SELECT
        |   key_id,
        |   operation_hash,
        |   key_usage,
        |   key_index
        | FROM public.prism_did_hd_key
        | WHERE did = $did
        """.stripMargin
        .query[(String, ArraySeq[Byte], VerificationRelationship | InternalKeyPurpose, Int)]
        .to[List]

    for {
      state <- getManagedDIDState(did)
      paths <- cxnIO.walletTransact(xa)
    } yield state.map(_.didIndex).fold(Nil) { didIndex =>
      paths.map { (keyId, operationHash, keyUsage, keyIndex) =>
        (keyId, operationHash, ManagedDIDHdKeyPath(didIndex, keyUsage, keyIndex))
      }
    }
  }

  override def insertHdKeyPath(
      did: PrismDID,
      keyId: String,
      hdKeyPath: ManagedDIDHdKeyPath,
      operationHash: Array[Byte]
  ): RIO[WalletAccessContext, Unit] = {
    val cxnIO = (now: Instant) => sql"""
          | INSERT INTO public.prism_did_hd_key(did, key_id, key_usage, key_index, created_at, operation_hash)
          | VALUES
          | (
          |  $did,
          |  $keyId,
          |  ${hdKeyPath.keyUsage},
          |  ${hdKeyPath.keyIndex},
          |  $now,
          |  $operationHash
          | )
          | ON CONFLICT (did, key_id, operation_hash) DO NOTHING
          |""".stripMargin.update

    for {
      now <- Clock.instant
      _ <- cxnIO(now).run.walletTransact(xa)
    } yield ()
  }

  override def listManagedDID(
      offset: Option[Int],
      limit: Option[Int]
  ): RIO[WalletAccessContext, (Seq[(PrismDID, ManagedDIDState)], Int)] = {
    val countCxnIO = (walletId: WalletId) =>
      sql"""
        | SELECT COUNT(*)
        | FROM public.prism_did_wallet_state
        | WHERE wallet_id = $walletId
        """.stripMargin
        .query[Int]
        .unique

    val didsCxnIO = (walletId: WalletId) => {
      val baseFr =
        sql"""
           | SELECT
           |   did,
           |   publication_status,
           |   atala_operation_content,
           |   publish_operation_id,
           |   created_at,
           |   updated_at,
           |   key_mode,
           |   did_index,
           |   wallet_id
           | FROM public.prism_did_wallet_state
           | WHERE wallet_id = $walletId
           | ORDER BY created_at
           """.stripMargin
      val withOffsetFr = offset.fold(baseFr)(offsetValue => baseFr ++ fr"OFFSET $offsetValue")
      val withOffsetAndLimitFr = limit.fold(withOffsetFr)(limitValue => withOffsetFr ++ fr"LIMIT $limitValue")

      withOffsetAndLimitFr
        .query[DIDStateRow]
        .to[List]
    }

    val cxnOps = (walletId: WalletId) =>
      for {
        totalCount <- countCxnIO(walletId)
        rows <- didsCxnIO(walletId)
      } yield (rows, totalCount)

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      result <- cxnOps(walletCtx.walletId)
        .walletTransact(xa)
        .flatMap { case (rows, totalCount) =>
          val results = rows.map(row => row.toDomain.map(row.did -> _))
          ZIO.foreach(results)(ZIO.fromTry).map(_ -> totalCount)
        }
    } yield result
  }

  // TODO: isolate wallet
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

    cxnIO.run.walletTransact(xa).unit
  }

  override def listUpdateLineage(
      did: Option[PrismDID],
      status: Option[ScheduledDIDOperationStatus]
  ): RIO[WalletAccessContext, Seq[DIDUpdateLineage]] = {
    val didFilter = did.map(d => fr"ws.did = $d")
    val statusFilter = status.map(s => fr"ul.status = $s")
    val walletIdFilter = (walletId: WalletId) => fr"ws.wallet_id = $walletId"
    val whereFr = (walletId: WalletId) => Fragments.whereAndOpt(didFilter, statusFilter, Some(walletIdFilter(walletId)))
    val baseFr = sql"""
           | SELECT
           |   ul.operation_id,
           |   ul.operation_hash,
           |   ul.previous_operation_hash,
           |   ul.status,
           |   ul.created_at,
           |   ul.updated_at
           | FROM public.prism_did_wallet_state ws
           |   LEFT JOIN public.prism_did_update_lineage ul ON ws.did = ul.did
           """.stripMargin

    for {
      walletCtx <- ZIO.service[WalletAccessContext]
      result <- (baseFr ++ whereFr(walletCtx.walletId))
        .query[DIDUpdateLineage]
        .to[List]
        .walletTransact(xa)
    } yield result
  }

  // TODO: isolate wallet
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

    Clock.instant.flatMap(now => cxnIO(now).run.walletTransact(xa)).unit
  }

}

object JdbcDIDNonSecretStorage {
  val layer: URLayer[Transactor[WalletTask], DIDNonSecretStorage] = ZLayer.fromFunction(new JdbcDIDNonSecretStorage(_))
}
