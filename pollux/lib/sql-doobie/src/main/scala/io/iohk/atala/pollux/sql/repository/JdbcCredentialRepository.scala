package io.iohk.atala.pollux.sql.repository

import cats.data.NonEmptyList
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.iohk.atala.castor.core.model.did.*
import io.iohk.atala.mercury.protocol.issuecredential.{IssueCredential, OfferCredential, RequestCredential}
import io.iohk.atala.pollux.anoncreds.CredentialRequestMetadata
import io.iohk.atala.pollux.core.model.*
import io.iohk.atala.pollux.core.model.error.CredentialRepositoryError
import io.iohk.atala.pollux.core.model.error.CredentialRepositoryError.*
import io.iohk.atala.pollux.core.repository.CredentialRepository
import io.iohk.atala.shared.db.ContextAwareTask
import io.iohk.atala.shared.db.Implicits.*
import io.iohk.atala.shared.models.WalletAccessContext
import org.postgresql.util.PSQLException
import zio.*
import zio.json.*

import doobie.free.connection
import java.time.Instant
import zio.interop.catz.*

class JdbcCredentialRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task], maxRetries: Int)
    extends CredentialRepository {

  // Uncomment to have Doobie LogHandler in scope and automatically output SQL statements in logs
  // given logHandler: LogHandler = LogHandler.jdkLogHandler

  import IssueCredentialRecord.*

  given didCommIDGet: Get[DidCommID] = Get[String].map(DidCommID(_))
  given didCommIDPut: Put[DidCommID] = Put[String].contramap(_.value)

  given credentialFormatGet: Get[CredentialFormat] = Get[String].map(CredentialFormat.valueOf)
  given credentialFormatPut: Put[CredentialFormat] = Put[String].contramap(_.toString)

  given protocolStateGet: Get[ProtocolState] = Get[String].map(ProtocolState.valueOf)
  given protocolStatePut: Put[ProtocolState] = Put[String].contramap(_.toString)

  given roleGet: Get[Role] = Get[String].map(Role.valueOf)
  given rolePut: Put[Role] = Put[String].contramap(_.toString)

  given offerCredentialGet: Get[OfferCredential] = Get[String].map(decode[OfferCredential](_).getOrElse(???))
  given offerCredentialPut: Put[OfferCredential] = Put[String].contramap(_.asJson.toString)

  given requestCredentialGet: Get[RequestCredential] = Get[String].map(decode[RequestCredential](_).getOrElse(???))
  given requestCredentialPut: Put[RequestCredential] = Put[String].contramap(_.asJson.toString)

  given acRequestMetadataGet: Get[CredentialRequestMetadata] =
    Get[String].map(_.fromJson[CredentialRequestMetadata].getOrElse(???))
  given acRequestMetadataPut: Put[CredentialRequestMetadata] = Put[String].contramap(_.toJson)

  given issueCredentialGet: Get[IssueCredential] = Get[String].map(decode[IssueCredential](_).getOrElse(???))
  given issueCredentialPut: Put[IssueCredential] = Put[String].contramap(_.asJson.toString)

  given prismDIDGet: Get[CanonicalPrismDID] =
    Get[String].map(s => PrismDID.fromString(s).fold(e => throw RuntimeException(e), _.asCanonical))
  given prismDIDPut: Put[CanonicalPrismDID] = Put[String].contramap(_.toString)

  override def createIssueCredentialRecord(record: IssueCredentialRecord): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | INSERT INTO public.issue_credential_records(
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   schema_id,
        |   credential_definition_id,
        |   credential_format,
        |   role,
        |   subject_id,
        |   validity_period,
        |   automatic_issuance,
        |   protocol_state,
        |   offer_credential_data,
        |   request_credential_data,
        |   ac_request_credential_metadata,
        |   issue_credential_data,
        |   issued_credential_raw,
        |   issuing_did,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure,
        |   wallet_id
        | ) values (
        |   ${record.id},
        |   ${record.createdAt},
        |   ${record.updatedAt},
        |   ${record.thid},
        |   ${record.schemaId},
        |   ${record.credentialDefinitionId},
        |   ${record.credentialFormat},
        |   ${record.role},
        |   ${record.subjectId},
        |   ${record.validityPeriod},
        |   ${record.automaticIssuance},
        |   ${record.protocolState},
        |   ${record.offerCredentialData},
        |   ${record.requestCredentialData},
        |   ${record.anonCredsRequestMetadata},
        |   ${record.issueCredentialData},
        |   ${record.issuedCredentialRaw},
        |   ${record.issuingDID},
        |   ${record.metaRetries},
        |   ${record.metaNextRetry},
        |   ${record.metaLastFailure},
        |   current_setting('app.current_wallet_id')::UUID
        | )
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .mapError {
        case e: PSQLException => CredentialRepositoryError.fromPSQLException(e.getSQLState, e.getMessage)
        case e                => e
      }
  }

  override def getIssueCredentialRecords(
      ignoreWithZeroRetries: Boolean,
      offset: Option[Int],
      limit: Option[Int]
  ): RIO[WalletAccessContext, (Seq[IssueCredentialRecord], Int)] = {
    val conditionFragment = Fragments.whereAndOpt(
      Option.when(ignoreWithZeroRetries)(fr"meta_retries > 0")
    )
    val baseFragment =
      sql"""
           | SELECT
           |   id,
           |   created_at,
           |   updated_at,
           |   thid,
           |   schema_id,
           |   credential_definition_id,
           |   credential_format,
           |   role,
           |   subject_id,
           |   validity_period,
           |   automatic_issuance,
           |   protocol_state,
           |   offer_credential_data,
           |   request_credential_data,
           |   ac_request_credential_metadata,
           |   issue_credential_data,
           |   issued_credential_raw,
           |   issuing_did,
           |   meta_retries,
           |   meta_next_retry,
           |   meta_last_failure
           | FROM public.issue_credential_records
           | $conditionFragment
           | ORDER BY created_at
        """.stripMargin
    val withOffsetFragment = offset.fold(baseFragment)(offsetValue => baseFragment ++ fr"OFFSET $offsetValue")
    val withOffsetAndLimitFragment =
      limit.fold(withOffsetFragment)(limitValue => withOffsetFragment ++ fr"LIMIT $limitValue")

    val countCxnIO =
      sql"""
           | SELECT COUNT(*)
           | FROM public.issue_credential_records
           | $conditionFragment
           """.stripMargin
        .query[Int]
        .unique

    val cxnIO = withOffsetAndLimitFragment
      .query[IssueCredentialRecord]
      .to[Seq]

    val effect = for {
      totalCount <- countCxnIO
      records <- cxnIO
    } yield (records, totalCount)

    effect.transactWallet(xa)
  }

  private def getRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): ConnectionIO[Seq[IssueCredentialRecord]] = {
    states match
      case Nil =>
        connection.pure(Nil)
      case head +: tail =>
        val nel = NonEmptyList.of(head, tail: _*)
        val inClauseFragment = Fragments.in(fr"protocol_state", nel)
        val conditionFragment = Fragments.andOpt(
          Some(inClauseFragment),
          Option.when(ignoreWithZeroRetries)(fr"meta_retries > 0")
        )
        val cxnIO = sql"""
            | SELECT
            |   id,
            |   created_at,
            |   updated_at,
            |   thid,
            |   schema_id,
            |   credential_definition_id,
            |   credential_format,
            |   role,
            |   subject_id,
            |   validity_period,
            |   automatic_issuance,
            |   protocol_state,
            |   offer_credential_data,
            |   request_credential_data,
            |   ac_request_credential_metadata,
            |   issue_credential_data,
            |   issued_credential_raw,
            |   issuing_did,
            |   meta_retries,
            |   meta_next_retry,
            |   meta_last_failure
            | FROM public.issue_credential_records
            | WHERE $conditionFragment
            | ORDER BY created_at
            | LIMIT $limit
            """.stripMargin
          .query[IssueCredentialRecord]
          .to[Seq]
        cxnIO
  }
  override def getIssueCredentialRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): RIO[WalletAccessContext, Seq[IssueCredentialRecord]] = {
    getRecordsByStates(ignoreWithZeroRetries, limit, states: _*).transactWallet(xa)
  }

  override def getIssueCredentialRecordsByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): Task[Seq[IssueCredentialRecord]] = {
    getRecordsByStates(ignoreWithZeroRetries, limit, states: _*).transact(xb)
  }
  override def getIssueCredentialRecord(
      recordId: DidCommID
  ): RIO[WalletAccessContext, Option[IssueCredentialRecord]] = {
    val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   schema_id,
        |   credential_definition_id,
        |   credential_format,
        |   role,
        |   subject_id,
        |   validity_period,
        |   automatic_issuance,
        |   protocol_state,
        |   offer_credential_data,
        |   request_credential_data,
        |   ac_request_credential_metadata,
        |   issue_credential_data,
        |   issued_credential_raw,
        |   issuing_did,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.issue_credential_records
        | WHERE id = $recordId
        """.stripMargin
      .query[IssueCredentialRecord]
      .option

    cxnIO
      .transactWallet(xa)
  }

  override def getIssueCredentialRecordByThreadId(
      thid: DidCommID,
      ignoreWithZeroRetries: Boolean,
  ): RIO[WalletAccessContext, Option[IssueCredentialRecord]] = {
    val conditionFragment = Fragments.whereAndOpt(
      Some(fr"thid = $thid"),
      Option.when(ignoreWithZeroRetries)(fr"meta_retries > 0")
    )
    val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   schema_id,
        |   credential_definition_id,
        |   credential_format,
        |   role,
        |   subject_id,
        |   validity_period,
        |   automatic_issuance,
        |   protocol_state,
        |   offer_credential_data,
        |   request_credential_data,
        |   ac_request_credential_metadata,
        |   issue_credential_data,
        |   issued_credential_raw,
        |   issuing_did,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.issue_credential_records
        | $conditionFragment
        """.stripMargin
      .query[IssueCredentialRecord]
      .option

    cxnIO
      .transactWallet(xa)
  }

  override def updateCredentialRecordProtocolState(
      recordId: DidCommID,
      from: IssueCredentialRecord.ProtocolState,
      to: IssueCredentialRecord.ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   protocol_state = $to,
        |   updated_at = ${Instant.now},
        |   meta_retries = $maxRetries,
        |   meta_next_retry = ${Instant.now},
        |   meta_last_failure = null
        | WHERE
        |   id = $recordId
        |   AND protocol_state = $from
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  def updateWithSubjectId(
      recordId: DidCommID,
      subjectId: String,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   protocol_state = $protocolState,
        |   subject_id = ${Some(subjectId)},
        |   updated_at = ${Instant.now}
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithJWTRequestCredential(
      recordId: DidCommID,
      request: RequestCredential,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   request_credential_data = $request,
        |   protocol_state = $protocolState,
        |   updated_at = ${Instant.now}
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithAnonCredsRequestCredential(
      recordId: DidCommID,
      request: RequestCredential,
      metadata: CredentialRequestMetadata,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO =
      sql"""
           | UPDATE public.issue_credential_records
           | SET
           |   request_credential_data = $request,
           |   ac_request_credential_metadata = $metadata,
           |   protocol_state = $protocolState,
           |   updated_at = ${Instant.now}
           | WHERE
           |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithIssueCredential(
      recordId: DidCommID,
      issue: IssueCredential,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   issue_credential_data = $issue,
        |   protocol_state = $protocolState,
        |   updated_at = ${Instant.now}
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def getValidIssuedCredentials(
      recordIds: Seq[DidCommID]
  ): RIO[WalletAccessContext, Seq[ValidIssuedCredentialRecord]] = {
    val idAsStrings = recordIds.map(_.toString)
    val nel = NonEmptyList.of(idAsStrings.head, idAsStrings.tail: _*)
    val inClauseFragment = Fragments.in(fr"id", nel)

    val cxnIO = sql"""
        | SELECT
        |   id,
        |   issued_credential_raw,
        |   subject_id
        | FROM public.issue_credential_records
        | WHERE
        |   issued_credential_raw IS NOT NULL
        |   AND $inClauseFragment
        """.stripMargin
      .query[ValidIssuedCredentialRecord]
      .to[Seq]

    cxnIO
      .transactWallet(xa)

  }

  override def deleteIssueCredentialRecord(recordId: DidCommID): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
      | DELETE
      | FROM public.issue_credential_records
      | WHERE id = $recordId
      """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithIssuedRawCredential(
      recordId: DidCommID,
      issue: IssueCredential,
      issuedRawCredential: String,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   issue_credential_data = $issue,
        |   issued_credential_raw = $issuedRawCredential,
        |   protocol_state = $protocolState,
        |   updated_at = ${Instant.now}
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  def updateAfterFail(
      recordId: DidCommID,
      failReason: Option[String]
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.issue_credential_records
        | SET
        |   meta_retries = CASE WHEN (meta_retries > 1) THEN meta_retries - 1 ELSE 0 END,
        |   meta_next_retry = CASE WHEN (meta_retries > 1) THEN ${Instant.now().plusSeconds(60)} ELSE null END,
        |   meta_last_failure = ${failReason}
        | WHERE
        |   id = $recordId
        """.stripMargin.update
    cxnIO.run.transactWallet(xa)
  }
}

object JdbcCredentialRepository {
  val maxRetries = 5 // TODO Move to config
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], CredentialRepository] =
    ZLayer.fromFunction(new JdbcCredentialRepository(_, _, maxRetries))
}
