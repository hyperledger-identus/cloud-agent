package org.hyperledger.identus.connect.core.service

import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError.InvalidStateForOperation
import org.hyperledger.identus.connect.core.model.ConnectionRecord.*
import org.hyperledger.identus.connect.core.repository.ConnectionRepositoryInMemory
import org.hyperledger.identus.mercury.model.{DidId, Message}
import org.hyperledger.identus.mercury.protocol.connection.ConnectionResponse
import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.WalletIdAndRecordId
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID

object ConnectionServiceImplSpec extends ZIOSpecDefault {

  val connectionServiceLayer = ConnectionRepositoryInMemory.layer >>> ConnectionServiceImpl.layer

  override def spec = {
    suite("ConnectionServiceImpl")(
      test("createConnectionInvitation creates a valid inviter connection record") {
        for {
          svc <- ZIO.service[ConnectionService]
          did = DidId("did:peer:INVITER")
          record <- svc.createConnectionInvitation(
            Some("Test connection invitation"),
            Some("Test goal code"),
            Some("Test goal"),
            did
          )
        } yield {
          assertTrue(record.label.contains("Test connection invitation")) &&
          assertTrue(record.protocolState == ProtocolState.InvitationGenerated) &&
          assertTrue(record.role == Role.Inviter) &&
          assertTrue(record.connectionRequest.isEmpty) &&
          assertTrue(record.connectionResponse.isEmpty) &&
          assertTrue(record.thid == record.id.toString) &&
          assertTrue(record.updatedAt.isEmpty) &&
          assertTrue(record.invitation.from == did) &&
          assertTrue(record.invitation.attachments.isEmpty) &&
          assertTrue(record.invitation.body.goal_code == Some("Test goal code")) &&
          assertTrue(record.invitation.body.goal == Some("Test goal")) &&
          assertTrue(record.invitation.body.accept.isEmpty)
        }
      }, {
        test("getConnectionRecord correctly returns record") {
          for {
            svc <- ZIO.service[ConnectionService]
            createdRecord <- svc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            foundRecord <- svc.findRecordById(createdRecord.id)
            notFoundRecord <- svc.findRecordById(UUID.randomUUID)
          } yield {
            assertTrue(foundRecord.contains(createdRecord)) &&
            assertTrue(notFoundRecord.isEmpty)
          }
        }
      }, {
        test("getConnectionRecords correctly returns all records") {
          for {
            svc <- ZIO.service[ConnectionService]
            createdRecord1 <- svc.createConnectionInvitation(
              Some("Test connection invitation #1"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            createdRecord2 <- svc.createConnectionInvitation(
              Some("Test connection invitation #2"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            records <- svc.findAllRecords()
          } yield {
            assertTrue(records.size == 2) &&
            assertTrue(records.contains(createdRecord1)) &&
            assertTrue(records.contains(createdRecord2))
          }
        }
      }, {
        test("scoped layers do not mix data") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Inviter"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.createConnectionInvitation(
              Some("Invitee"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITEE")
            )
            allInviterRecords <- inviterSvc.findAllRecords()
            allInviteeRecords <- inviteeSvc.findAllRecords()
          } yield {
            assertTrue(allInviterRecords.size == 1) &&
            assertTrue(allInviterRecords.head == inviterRecord) &&
            assertTrue(allInviteeRecords.size == 1) &&
            assertTrue(allInviteeRecords.head == inviteeRecord)
          }
        }
      }, {
        test("receiveConnectionInvitation should correctly create a new invitee record") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            allInviteeRecords <- inviteeSvc.findAllRecords()
          } yield {
            assertTrue(allInviteeRecords.head == inviteeRecord) &&
            assertTrue(inviteeRecord.label.isEmpty) &&
            assertTrue(inviteeRecord.protocolState == ProtocolState.InvitationReceived) &&
            assertTrue(inviteeRecord.role == Role.Invitee) &&
            assertTrue(inviteeRecord.connectionRequest.isEmpty) &&
            assertTrue(inviteeRecord.connectionResponse.isEmpty) &&
            assertTrue(inviteeRecord.thid == inviterRecord.invitation.id) &&
            assertTrue(inviteeRecord.updatedAt.isEmpty) &&
            assertTrue(inviteeRecord.invitation == inviterRecord.invitation)
          }
        }
      }, {
        test("acceptConnectionInvitation should return an error for an unknown recordId") {
          for {
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            unknownRecordId = UUID.randomUUID()
            inviteeRecord <- inviteeSvc.acceptConnectionInvitation(unknownRecordId, DidId("did:peer:INVITEE")).exit
          } yield {
            assert(inviteeRecord)(fails(equalTo(ConnectionServiceError.RecordIdNotFound(unknownRecordId))))
          }
        }
      }, {
        test("acceptConnectionInvitation should update the invitee record accordingly") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            maybeInviteeRecord <- inviteeSvc.acceptConnectionInvitation(
              inviteeRecord.id,
              DidId("did:peer:INVITEE")
            )
            allInviteeRecords <- inviteeSvc.findAllRecords()
          } yield {
            val updatedRecord = maybeInviteeRecord
            assertTrue(allInviteeRecords.head == updatedRecord) &&
            assertTrue(updatedRecord.updatedAt.isDefined) &&
            assertTrue(updatedRecord.protocolState == ProtocolState.ConnectionRequestPending) &&
            assertTrue(updatedRecord.connectionRequest.isDefined)
          }
        }
      }, {
        test("receiveConnectionRequest should update the inviter record accordingly") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            maybeAcceptedInvitationRecord <- inviteeSvc.acceptConnectionInvitation(
              inviteeRecord.id,
              DidId("did:peer:INVITEE")
            )
            // FIXME: Should the service return an Option while we have dedicated "not found" error for that case !?
            connectionRequest = maybeAcceptedInvitationRecord.connectionRequest.get
            maybeReceivedRequestConnectionRecord <- inviterSvc.receiveConnectionRequest(connectionRequest, None)
            allInviterRecords <- inviterSvc.findAllRecords()
          } yield {
            val updatedRecord = maybeReceivedRequestConnectionRecord
            assertTrue(allInviterRecords.head == updatedRecord) &&
            assertTrue(updatedRecord.updatedAt.isDefined) &&
            assertTrue(updatedRecord.protocolState == ProtocolState.ConnectionRequestReceived) &&
            assertTrue(updatedRecord.connectionRequest.isDefined) &&
            assertTrue(updatedRecord.connectionResponse.isEmpty)
          }
        }
      }, {
        test("receiveConnectionRequest should update the inviter record accordingly if Invitation Not expired") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            maybeAcceptedInvitationRecord <- inviteeSvc.acceptConnectionInvitation(
              inviteeRecord.id,
              DidId("did:peer:INVITEE")
            )
            connectionRequest = maybeAcceptedInvitationRecord.connectionRequest.get
            connectionRecordUpdated <- inviterSvc.markConnectionInvitationExpired(inviterRecord.id)
            expiryTime = Duration.fromSeconds(300)
            exit <- inviterSvc.receiveConnectionRequest(connectionRequest, Some(expiryTime)).exit

          } yield {
            assertTrue(exit match
              case Exit.Failure(Cause.Fail(_: InvalidStateForOperation, _)) => true
              case _                                                        => false)

          }
        }
      }, {
        test("acceptingConnectionRequest should update the inviter record accordingly") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            maybeAcceptedInvitationRecord <- inviteeSvc.acceptConnectionInvitation(
              inviteeRecord.id,
              DidId("did:peer:INVITEE")
            )
            connectionRequest = maybeAcceptedInvitationRecord.connectionRequest.get
            expiryTime = Duration.fromSeconds(300)
            maybeReceivedRequestConnectionRecord <- inviterSvc.receiveConnectionRequest(
              connectionRequest,
              Some(expiryTime)
            )
            maybeAcceptedRequestConnectionRecord <- inviterSvc.acceptConnectionRequest(inviterRecord.id)
            allInviterRecords <- inviterSvc.findAllRecords()
          } yield {
            val updatedRecord = maybeAcceptedRequestConnectionRecord
            assertTrue(allInviterRecords.head == updatedRecord) &&
            assertTrue(
              updatedRecord.updatedAt.forall(_.isAfter(maybeReceivedRequestConnectionRecord.updatedAt.get))
            ) &&
            assertTrue(updatedRecord.protocolState == ProtocolState.ConnectionResponsePending) &&
            assertTrue(updatedRecord.connectionRequest.isDefined) &&
            assertTrue(updatedRecord.connectionResponse.isDefined)
          }
        }
      }, {
        test("receiveConnectionResponse should update the invitee record accordingly") {
          for {
            inviterSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviteeSvc <- ZIO.service[ConnectionService].provideLayer(connectionServiceLayer)
            inviterRecord <- inviterSvc.createConnectionInvitation(
              Some("Test connection invitation"),
              Some("Test goal code"),
              Some("Test goal"),
              DidId("did:peer:INVITER")
            )
            inviteeRecord <- inviteeSvc.receiveConnectionInvitation(inviterRecord.invitation.toBase64)
            maybeAcceptedInvitationRecord <- inviteeSvc.acceptConnectionInvitation(
              inviteeRecord.id,
              DidId("did:peer:INVITEE")
            )
            connectionRequest = maybeAcceptedInvitationRecord.connectionRequest.get
            _ <- inviteeSvc.markConnectionRequestSent(inviteeRecord.id)
            expiryTime = Duration.fromSeconds(300)

            maybeReceivedRequestConnectionRecord <- inviterSvc.receiveConnectionRequest(
              connectionRequest,
              Some(expiryTime)
            )
            maybeAcceptedRequestConnectionRecord <- inviterSvc.acceptConnectionRequest(inviterRecord.id)
            connectionResponseMessage <- ZIO.fromEither(
              maybeAcceptedRequestConnectionRecord.connectionResponse.get.makeMessage.toJson.fromJson[Message]
            )
            _ <- inviterSvc.markConnectionResponseSent(inviterRecord.id)
            maybeReceivedResponseConnectionRecord <- inviteeSvc.receiveConnectionResponse(
              ConnectionResponse.fromMessage(connectionResponseMessage).toOption.get
            )
            allInviteeRecords <- inviteeSvc.findAllRecords()
          } yield {
            val updatedRecord = maybeReceivedResponseConnectionRecord
            assertTrue(allInviteeRecords.head == updatedRecord) &&
            assertTrue(
              updatedRecord.updatedAt.forall(_.isAfter(maybeAcceptedInvitationRecord.updatedAt.get))
            ) &&
            assertTrue(updatedRecord.protocolState == ProtocolState.ConnectionResponseReceived) &&
            assertTrue(updatedRecord.connectionRequest.isDefined) &&
            assertTrue(updatedRecord.connectionResponse.isDefined)
          }
        }
      }
    ).provide(
      connectionServiceLayer,
      messaging.MessagingServiceConfig.inMemoryLayer,
      messaging.MessagingService.serviceLayer,
      messaging.MessagingService.producerLayer[UUID, WalletIdAndRecordId],
      ZLayer.succeed(WalletAccessContext(WalletId.random)),
      zio.Scope.default
    )
  }

}
