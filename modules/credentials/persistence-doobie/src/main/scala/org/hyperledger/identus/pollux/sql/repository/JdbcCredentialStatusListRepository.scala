package org.hyperledger.identus.pollux.sql.repository

import cats.implicits.toFunctorOps
import doobie.*
import doobie.free.connection.ConnectionOp
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.repository.CredentialStatusListRepository
import org.hyperledger.identus.pollux.vc.jwt.{Issuer, StatusPurpose}
import org.hyperledger.identus.pollux.vc.jwt.revocation.BitString
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.db.Implicits.given
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.interop.catz.*

import java.time.Instant
import java.util.{Objects, UUID}

class JdbcCredentialStatusListRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task])
    extends CredentialStatusListRepository {

  def findById(id: UUID): UIO[Option[CredentialStatusList]] = {
    val cxnIO =
      sql"""
           | SELECT
           |   id,
           |   wallet_id,
           |   issuer,
           |   issued,
           |   purpose,
           |   status_list_credential,
           |   size,
           |   last_used_index,
           |   created_at,
           |   updated_at
           |  FROM public.credential_status_lists where id = $id
           |""".stripMargin
        .query[CredentialStatusList]
        .option

    cxnIO
      .transact(xb)
      .orDie
  }

  override def incrementAndGetStatusListIndex(
      jwtIssuer: Issuer,
      statusListRegistryUrl: String
  ): URIO[WalletAccessContext, (UUID, Int)] = {

    def acquireAdvisoryLock(walletId: WalletId): ConnectionIO[Unit] = {
      // Should be specific to this process
      val PROCESS_UNIQUE_ID = 235457
      val hashCode = Objects.hash(walletId.hashCode(), PROCESS_UNIQUE_ID)
      sql"SELECT pg_advisory_xact_lock($hashCode)".query[Unit].unique.void
    }

    def getLatestOfTheWallet: ConnectionIO[Option[CredentialStatusList]] =
      sql"""
           | SELECT
           |   id,
           |   wallet_id,
           |   issuer,
           |   issued,
           |   purpose,
           |   status_list_credential,
           |   size,
           |   last_used_index,
           |   created_at,
           |   updated_at
           |  FROM public.credential_status_lists
           |  ORDER BY created_at DESC limit 1
           |""".stripMargin
        .query[CredentialStatusList]
        .option

    def createNewForTheWallet(
        id: UUID,
        issuerDid: String,
        issued: Instant,
        credentialStr: String
    ): ConnectionIO[CredentialStatusList] =
      sql"""
           |INSERT INTO public.credential_status_lists (
           |  id,
           |  issuer,
           |  issued,
           |  purpose,
           |  status_list_credential,
           |  size,
           |  last_used_index,
           |  wallet_id
           | )
           |VALUES (
           |  $id,
           |  $issuerDid,
           |  $issued,
           |  ${StatusPurpose.Revocation}::public.enum_credential_status_list_purpose,
           |  $credentialStr::JSON,
           |  ${BitString.MIN_SL2021_SIZE},
           |  0,
           |  current_setting('app.current_wallet_id')::UUID
           | )
           |RETURNING id, wallet_id, issuer, issued, purpose, status_list_credential, size, last_used_index, created_at, updated_at
             """.stripMargin
        .query[CredentialStatusList]
        .unique

    def updateLastUsedIndex(statusListId: UUID, lastUsedIndex: Int): ConnectionIO[Int] =
      sql"""
           | UPDATE public.credential_status_lists
           | SET
           |   last_used_index = $lastUsedIndex,
           |   updated_at = ${Instant.now()}
           | WHERE
           |   id = $statusListId
           |""".stripMargin.update.run

    (for {
      id <- ZIO.succeed(UUID.randomUUID())
      newStatusListVC <- createStatusListVC(jwtIssuer, statusListRegistryUrl, id)
      walletCtx <- ZIO.service[WalletAccessContext]
      walletId = walletCtx.walletId
      cnxIO = for {
        _ <- acquireAdvisoryLock(walletId)
        maybeStatusList <- getLatestOfTheWallet
        statusList <- maybeStatusList match
          case Some(csl) if csl.lastUsedIndex < csl.size => cats.free.Free.pure[ConnectionOp, CredentialStatusList](csl)
          case _ => createNewForTheWallet(id, jwtIssuer.did.toString, Instant.now(), newStatusListVC)
        newIndex = statusList.lastUsedIndex + 1
        _ <- updateLastUsedIndex(statusList.id, newIndex)
      } yield (statusList.id, newIndex)
      result <- cnxIO.transactWallet(xa)
    } yield result).orDie
  }

  def allocateSpaceForCredential(
      issueCredentialRecordId: DidCommID,
      credentialStatusListId: UUID,
      statusListIndex: Int
  ): URIO[WalletAccessContext, Unit] = {

    val statusListEntryCreationQuery =
      sql"""
           | INSERT INTO public.credentials_in_status_list (
           |   id,
           |   issue_credential_record_id,
           |   credential_status_list_id,
           |   status_list_index,
           |   is_canceled
           | )
           | VALUES (
           |   ${UUID.randomUUID()},
           |   $issueCredentialRecordId,
           |   $credentialStatusListId,
           |   $statusListIndex,
           |   false
           | )
           |""".stripMargin.update.run

    val statusListUpdateQuery =
      sql"""
           | UPDATE public.credential_status_lists
           | SET
           |   last_used_index = $statusListIndex,
           |   updated_at = ${Instant.now()}
           | WHERE
           |   id = $credentialStatusListId
           |""".stripMargin.update.run

    val res: ConnectionIO[Unit] = for {
      _ <- statusListEntryCreationQuery
      _ <- statusListUpdateQuery
    } yield ()

    res
      .transactWallet(xa)
      .orDie

  }

  def existsForIssueCredentialRecordId(id: DidCommID): URIO[WalletAccessContext, Boolean] = {
    val cxnIO =
      sql"""
           | SELECT COUNT(*)
           |  FROM public.credentials_in_status_list
           |  WHERE issue_credential_record_id = $id
           |""".stripMargin
        .query[Int]
        .unique

    cxnIO
      .map(_ > 0)
      .transactWallet(xa)
      .orDie
  }

  def revokeByIssueCredentialRecordId(
      issueCredentialRecordId: DidCommID
  ): URIO[WalletAccessContext, Unit] = {
    for {
      walletId <- ZIO.service[WalletAccessContext].map(_.walletId)
      updateQuery =
        sql"""
             | UPDATE public.credentials_in_status_list AS cisl
             | SET is_canceled = true
             | FROM public.credential_status_lists AS csl
             | WHERE cisl.credential_status_list_id = csl.id
             | AND csl.wallet_id = ${walletId.toUUID}
             | AND cisl.issue_credential_record_id = $issueCredentialRecordId
             | AND cisl.is_canceled = false;
             |""".stripMargin.update.run
      _ <- updateQuery
        .transactWallet(xa)
        .ensureOneAffectedRowOrDie
    } yield ()
  }

  def getCredentialStatusListIds: UIO[Seq[(WalletId, UUID)]] = {
    val cxnIO =
      sql"""
           | SELECT 
           |    wallet_id,
           |    id
           |  FROM public.credential_status_lists
           |""".stripMargin
        .query[(WalletId, UUID)]
        .to[Seq]
    cxnIO
      .transact(xb)
      .orDie
  }

  def getCredentialStatusListsWithCreds(
      statusListId: UUID
  ): URIO[WalletAccessContext, CredentialStatusListWithCreds] = {
    val cxnIO =
      sql"""
           | SELECT
           |   csl.id as credential_status_list_id,
           |   csl.issuer,
           |   csl.issued,
           |   csl.purpose,
           |   csl.wallet_id,
           |   csl.status_list_credential,
           |   csl.size,
           |   csl.last_used_index,
           |   cisl.id as credential_in_status_list_id,
           |   cisl.issue_credential_record_id,
           |   cisl.status_list_index,
           |   cisl.is_canceled,
           |   cisl.is_processed
           |  FROM public.credential_status_lists csl
           |  LEFT JOIN public.credentials_in_status_list cisl ON csl.id = cisl.credential_status_list_id
           |  WHERE
           |    csl.id = $statusListId
           |""".stripMargin
        .query[CredentialStatusListWithCred]
        .to[List]
        .transactWallet(xa)
        .orDie

    cxnIO.map(items =>
      CredentialStatusListWithCreds(
        statusListId,
        items.head.walletId,
        items.head.issuer,
        items.head.issued,
        items.head.purpose,
        items.head.statusListCredential,
        items.head.size,
        items.head.lastUsedIndex,
        items.map { item =>
          CredInStatusList(
            item.credentialInStatusListId,
            item.issueCredentialRecordId,
            item.statusListIndex,
            item.isCanceled,
            item.isProcessed,
          )
        }
      )
    )
  }

  def updateStatusListCredential(
      credentialStatusListId: UUID,
      statusListCredential: String
  ): URIO[WalletAccessContext, Unit] = {

    val updateQuery =
      sql"""
           | UPDATE public.credential_status_lists
           | SET
           |   status_list_credential = $statusListCredential::JSON,
           |   updated_at = ${Instant.now()}
           | WHERE
           |   id = $credentialStatusListId
           |""".stripMargin.update.run

    updateQuery
      .transactWallet(xa)
      .unit
      .orDie

  }

  def markAsProcessedMany(
      credsInStatusListIds: Seq[UUID]
  ): URIO[WalletAccessContext, Unit] = {

    val updateQuery =
      sql"""
           | UPDATE public.credentials_in_status_list
           | SET
           |   is_processed = true
           | WHERE
           |   id::text IN (${credsInStatusListIds.map(_.toString).mkString(",")})
           |""".stripMargin.update.run

    if credsInStatusListIds.nonEmpty then updateQuery.transactWallet(xa).unit.orDie else ZIO.unit

  }

}

object JdbcCredentialStatusListRepository {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], CredentialStatusListRepository] =
    ZLayer.fromFunction(new JdbcCredentialStatusListRepository(_, _))
}
