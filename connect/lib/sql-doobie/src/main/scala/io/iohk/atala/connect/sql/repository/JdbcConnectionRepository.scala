package io.iohk.atala.connect.sql.repository

import cats.data.NonEmptyList
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.iohk.atala.connect.core.model.*
import io.iohk.atala.connect.core.model.ConnectionRecord.{ProtocolState, Role}
import io.iohk.atala.connect.core.model.error.ConnectionRepositoryError.*
import io.iohk.atala.connect.core.repository.ConnectionRepository
import io.iohk.atala.mercury.protocol.connection.*
import io.iohk.atala.mercury.protocol.invitation.v2.Invitation
import io.iohk.atala.shared.db.ContextAwareTask
import io.iohk.atala.shared.db.Implicits.*
import io.iohk.atala.shared.models.WalletAccessContext
import org.postgresql.util.PSQLException
import zio.*
import zio.interop.catz.*
import java.time.Instant
import java.util.UUID
import doobie.free.connection

class JdbcConnectionRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task]) extends ConnectionRepository {

  // given logHandler: LogHandler = LogHandler.jdkLogHandler

  given uuidGet: Get[UUID] = Get[String].map(UUID.fromString)
  given uuidPut: Put[UUID] = Put[String].contramap(_.toString())

  given protocolStateGet: Get[ProtocolState] = Get[String].map(ProtocolState.valueOf)
  given protocolStatePut: Put[ProtocolState] = Put[String].contramap(_.toString)

  given roleGet: Get[Role] = Get[String].map(Role.valueOf)
  given rolePut: Put[Role] = Put[String].contramap(_.toString)

  given invitationGet: Get[Invitation] = Get[String].map(decode[Invitation](_).getOrElse(???))
  given invitationPut: Put[Invitation] = Put[String].contramap(_.asJson.toString)

  given connectionRequestGet: Get[ConnectionRequest] = Get[String].map(decode[ConnectionRequest](_).getOrElse(???))
  given connectionRequestPut: Put[ConnectionRequest] = Put[String].contramap(_.asJson.toString)

  given connectionResponseGet: Get[ConnectionResponse] = Get[String].map(decode[ConnectionResponse](_).getOrElse(???))
  given connectionResponsePut: Put[ConnectionResponse] = Put[String].contramap(_.asJson.toString)

  override def createConnectionRecord(record: ConnectionRecord): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | INSERT INTO public.connection_records(
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   label,
        |   role,
        |   protocol_state,
        |   invitation,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure,
        |   wallet_id,
        |   goal_code,
        |   goal
        | ) values (
        |   ${record.id},
        |   ${record.createdAt},
        |   ${record.updatedAt},
        |   ${record.thid},
        |   ${record.label},
        |   ${record.role},
        |   ${record.protocolState},
        |   ${record.invitation},
        |   ${record.metaRetries},
        |   ${record.metaNextRetry},
        |   ${record.metaLastFailure},
        |   current_setting('app.current_wallet_id')::UUID,
        |   ${record.goalCode},
        |   ${record.goal}
        | )
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .mapError {
        case e: PSQLException => UniqueConstraintViolation(e.getMessage())
        case e                => e
      }
  }

  override def getConnectionRecords: RIO[WalletAccessContext, Seq[ConnectionRecord]] = {
    val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   label,
        |   goal_code,
        |   goal,
        |   role,
        |   protocol_state,
        |   invitation,
        |   connection_request,
        |   connection_response,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.connection_records
        | ORDER BY created_at
        """.stripMargin
      .query[ConnectionRecord]
      .to[Seq]

    cxnIO
      .transactWallet(xa)
  }

  override def getConnectionRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: ConnectionRecord.ProtocolState*
  ): RIO[WalletAccessContext, Seq[ConnectionRecord]] = {
    getRecordsByStates(ignoreWithZeroRetries, limit, states: _*).transactWallet(xa)
  }

  override def getConnectionRecordsByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: ConnectionRecord.ProtocolState*
  ): Task[Seq[ConnectionRecord]] = {
    getRecordsByStates(ignoreWithZeroRetries, limit, states: _*).transact(xb)
  }

  private def getRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: ConnectionRecord.ProtocolState*
  ): ConnectionIO[Seq[ConnectionRecord]] = {
    states match
      case Nil =>
        connection.pure(Nil)
      case head +: tail =>
        val nel = NonEmptyList.of(head, tail: _*)
        val inClauseFragment = Fragments.in(fr"protocol_state", nel)
        val conditionFragment = Fragments.whereAndOpt(
          Some(inClauseFragment),
          Option.when(ignoreWithZeroRetries)(fr"meta_retries > 0")
        )
        val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   label,
        |   goal_code,
        |   goal,
        |   role,
        |   protocol_state,
        |   invitation,
        |   connection_request,
        |   connection_response,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.connection_records
        | $conditionFragment
        | ORDER BY created_at
        | LIMIT $limit
        """.stripMargin
          .query[ConnectionRecord]
          .to[Seq]

        cxnIO
  }

  override def getConnectionRecord(recordId: UUID): RIO[WalletAccessContext, Option[ConnectionRecord]] = {
    val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   label,
        |   goal_code,
        |   goal,
        |   role,
        |   protocol_state,
        |   invitation,
        |   connection_request,
        |   connection_response,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.connection_records
        | WHERE id = $recordId
        """.stripMargin
      .query[ConnectionRecord]
      .option

    cxnIO
      .transactWallet(xa)
  }

  override def deleteConnectionRecord(recordId: UUID): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
      | DELETE
      | FROM public.connection_records
      | WHERE id = $recordId
      """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def getConnectionRecordByThreadId(thid: String): RIO[WalletAccessContext, Option[ConnectionRecord]] = {
    val cxnIO = sql"""
        | SELECT
        |   id,
        |   created_at,
        |   updated_at,
        |   thid,
        |   label,
        |   goal_code,
        |   goal,
        |   role,
        |   protocol_state,
        |   invitation,
        |   connection_request,
        |   connection_response,
        |   meta_retries,
        |   meta_next_retry,
        |   meta_last_failure
        | FROM public.connection_records
        | WHERE thid = $thid
        """.stripMargin // | WHERE thid = $thid OR id = $thid
      .query[ConnectionRecord]
      .option

    cxnIO
      .transactWallet(xa)
  }

  override def updateConnectionProtocolState(
      id: UUID,
      from: ConnectionRecord.ProtocolState,
      to: ConnectionRecord.ProtocolState,
      maxRetries: Int,
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.connection_records
        | SET
        |   protocol_state = $to,
        |   updated_at = ${Instant.now},
        |   meta_retries = ${maxRetries},
        |   meta_next_retry = ${Instant.now},
        |   meta_last_failure = null
        | WHERE
        |   id = $id
        |   AND protocol_state = $from
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithConnectionRequest(
      recordId: UUID,
      request: ConnectionRequest,
      state: ProtocolState,
      maxRetries: Int,
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.connection_records
        | SET
        |   connection_request = $request,
        |   protocol_state = $state,
        |   updated_at = ${Instant.now},
        |   meta_retries = ${maxRetries},
        |   meta_next_retry = ${Instant.now},
        |   meta_last_failure = null
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  override def updateWithConnectionResponse(
      recordId: UUID,
      response: ConnectionResponse,
      state: ProtocolState,
      maxRetries: Int,
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.connection_records
        | SET
        |   connection_response = $response,
        |   protocol_state = $state,
        |   updated_at = ${Instant.now},
        |   meta_retries = ${maxRetries},
        |   meta_next_retry = ${Instant.now},
        |   meta_last_failure = null
        | WHERE
        |   id = $recordId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
  }

  def updateAfterFail(
      recordId: UUID,
      failReason: Option[String],
  ): RIO[WalletAccessContext, Int] = {
    val cxnIO = sql"""
        | UPDATE public.connection_records
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
object JdbcConnectionRepository {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], ConnectionRepository] =
    ZLayer.fromFunction(new JdbcConnectionRepository(_, _))
}
