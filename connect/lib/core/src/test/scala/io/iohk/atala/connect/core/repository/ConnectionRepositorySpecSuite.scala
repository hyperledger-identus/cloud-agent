package io.iohk.atala.connect.core.repository

import io.iohk.atala.connect.core.model.ConnectionRecord
import io.iohk.atala.connect.core.model.ConnectionRecord._
import io.iohk.atala.connect.core.model.error.ConnectionRepositoryError._
import io.iohk.atala.mercury.model.DidId
import io.iohk.atala.mercury.protocol.connection.ConnectionRequest
import io.iohk.atala.mercury.protocol.connection.ConnectionResponse
import io.iohk.atala.mercury.protocol.invitation.v2.Invitation
import zio.Cause
import zio.Exit
import zio.Task
import zio.ZIO
import zio.test.Assertion._
import zio.test._

import java.time.Instant
import java.util.UUID

object ConnectionRepositorySpecSuite {

  private def connectionRecord = ConnectionRecord(
    UUID.randomUUID,
    Instant.ofEpochSecond(Instant.now.getEpochSecond()),
    None,
    None,
    None,
    ConnectionRecord.Role.Inviter,
    ConnectionRecord.ProtocolState.InvitationGenerated,
    Invitation(
      id = UUID.randomUUID().toString,
      from = DidId("did:prism:aaa"),
      body = Invitation.Body(goal_code = "connect", goal = "Establish a trust connection between two peers", Nil)
    ),
    None,
    None
  )

  private def connectionRequest = ConnectionRequest(
    from = DidId("did:prism:aaa"),
    to = DidId("did:prism:bbb"),
    thid = Some(UUID.randomUUID().toString),
    body = ConnectionRequest.Body(goal_code = Some("Connect"))
  )

  val testSuite = suite("CRUD operations")(
    test("createConnectionRecord creates a new record in DB") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        record = connectionRecord
        count <- repo.createConnectionRecord(record)
      } yield assertTrue(count == 1)
    },
    test("createConnectionRecord prevents creation of 2 records with the same thid") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        thid = UUID.randomUUID()
        aRecord = connectionRecord.copy(thid = Some(thid))
        bRecord = connectionRecord.copy(thid = Some(thid))
        aCount <- repo.createConnectionRecord(aRecord)
        bCount <- repo.createConnectionRecord(bRecord).exit
      } yield {
        assertTrue(bCount match
          case Exit.Failure(cause: Cause.Fail[_]) if cause.value.isInstanceOf[UniqueConstraintViolation] => true
          case _                                                                                         => false
        )
      }
    },
    test("getConnectionRecord correctly returns an existing record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        record <- repo.getConnectionRecord(bRecord.id)
      } yield assertTrue(record.contains(bRecord))
    },
    test("getConnectionRecord returns None for an unknown record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        record <- repo.getConnectionRecord(UUID.randomUUID())
      } yield assertTrue(record.isEmpty)
    },
    test("getConnectionRecords returns all records") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        records <- repo.getConnectionRecords()
      } yield {
        assertTrue(records.size == 2) &&
        assertTrue(records.contains(aRecord)) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("deleteRecord deletes an exsiting record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        count <- repo.deleteConnectionRecord(aRecord.id)
        records <- repo.getConnectionRecords()
      } yield {
        assertTrue(count == 1) &&
        assertTrue(records.size == 1) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("deleteRecord does nothing for an unknown record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        count <- repo.deleteConnectionRecord(UUID.randomUUID)
        records <- repo.getConnectionRecords()
      } yield {
        assertTrue(count == 0) &&
        assertTrue(records.size == 2) &&
        assertTrue(records.contains(aRecord)) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("getConnectionRecordByThreadId correctly returns an existing thid") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        thid = UUID.randomUUID()
        aRecord = connectionRecord.copy(thid = Some(thid))
        bRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        record <- repo.getConnectionRecordByThreadId(thid)
      } yield assertTrue(record.contains(aRecord))
    },
    test("getConnectionRecordByThreadId returns nothing for an unknown thid") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord.copy(thid = Some(UUID.randomUUID()))
        bRecord = connectionRecord.copy(thid = Some(UUID.randomUUID()))
        _ <- repo.createConnectionRecord(aRecord)
        _ <- repo.createConnectionRecord(bRecord)
        record <- repo.getConnectionRecordByThreadId(UUID.randomUUID())
      } yield assertTrue(record.isEmpty)
    },
    test("updateConnectionProtocolState updates the record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        record <- repo.getConnectionRecord(aRecord.id)
        count <- repo.updateConnectionProtocolState(
          aRecord.id,
          ProtocolState.InvitationGenerated,
          ProtocolState.ConnectionRequestReceived
        )
        updatedRecord <- repo.getConnectionRecord(aRecord.id)
      } yield {
        assertTrue(count == 1) &&
        assertTrue(record.get.protocolState == ProtocolState.InvitationGenerated) &&
        assertTrue(updatedRecord.get.protocolState == ProtocolState.ConnectionRequestReceived)
      }
    },
    test("updateConnectionProtocolState doesn't update the record for invalid states") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        record <- repo.getConnectionRecord(aRecord.id)
        count <- repo.updateConnectionProtocolState(
          aRecord.id,
          ProtocolState.ConnectionRequestPending,
          ProtocolState.ConnectionRequestReceived
        )
        updatedRecord <- repo.getConnectionRecord(aRecord.id)
      } yield {
        assertTrue(count == 0) &&
        assertTrue(record.get.protocolState == ProtocolState.InvitationGenerated) &&
        assertTrue(updatedRecord.get.protocolState == ProtocolState.InvitationGenerated)
      }
    },
    test("updateWithConnectionRequest updates record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        record <- repo.getConnectionRecord(aRecord.id)
        request = connectionRequest
        count <- repo.updateWithConnectionRequest(
          aRecord.id,
          request,
          ProtocolState.ConnectionRequestSent
        )
        updatedRecord <- repo.getConnectionRecord(aRecord.id)
      } yield {
        assertTrue(count == 1) &&
        assertTrue(record.get.connectionRequest.isEmpty) &&
        assertTrue(updatedRecord.get.connectionRequest.contains(request))
      }
    },
    test("updateWithConnectionResponse updates record") {
      for {
        repo <- ZIO.service[ConnectionRepository[Task]]
        aRecord = connectionRecord
        _ <- repo.createConnectionRecord(aRecord)
        record <- repo.getConnectionRecord(aRecord.id)
        response = ConnectionResponse.makeResponseFromRequest(connectionRequest.makeMessage)
        count <- repo.updateWithConnectionResponse(
          aRecord.id,
          response,
          ProtocolState.ConnectionResponseSent
        )
        updatedRecord <- repo.getConnectionRecord(aRecord.id)
      } yield {
        assertTrue(count == 1) &&
        assertTrue(record.get.connectionResponse.isEmpty) &&
        assertTrue(updatedRecord.get.connectionResponse.contains(response))
      }
    }
  )
}
