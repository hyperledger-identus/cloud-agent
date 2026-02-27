package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.presentproof.{Presentation, ProposePresentation, RequestPresentation}
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.PresentationRecord.*
import org.hyperledger.identus.pollux.core.service.serdes.{AnoncredCredentialProofV1, AnoncredCredentialProofsV1}
import org.hyperledger.identus.shared.models.*
import zio.{URIO, ZIO, ZLayer}
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID

object PresentationRepositorySpecSuite {
  val maxRetries = 5 // TODO Move to config

  private def presentationRecord: URIO[WalletAccessContext, PresentationRecord] = PresentationRecord
    .make(
      id = DidCommID(),
      createdAt = Instant.now,
      updatedAt = None,
      thid = DidCommID(),
      schemaId = None,
      connectionId = None,
      role = PresentationRecord.Role.Verifier,
      protocolState = PresentationRecord.ProtocolState.RequestPending,
      credentialFormat = CredentialFormat.JWT,
      invitation = None,
      requestPresentationData = None,
      proposePresentationData = None,
      presentationData = None,
      credentialsToUse = None,
      anoncredCredentialsToUseJsonSchemaId = None,
      anoncredCredentialsToUse = None,
      sdJwtClaimsToUseJsonSchemaId = None,
      sdJwtClaimsToDisclose = None,
      sdJwtDisclosedClaims = None,
      metaRetries = maxRetries,
      metaNextRetry = Some(Instant.now()),
      metaLastFailure = None
    )
    .map(_.withTruncatedTimestamp())

  private def requestPresentation = RequestPresentation(
    from = Some(DidId("did:prism:aaa")),
    to = Some(DidId("did:prism:bbb")),
    thid = Some(UUID.randomUUID.toString),
    body = RequestPresentation.Body(goal_code = Some("request Presentation")),
    attachments = Nil
  )

  private def proposePresentation = ProposePresentation(
    from = DidId("did:prism:aaa"),
    to = DidId("did:prism:bbb"),
    thid = Some(UUID.randomUUID.toString),
    body = ProposePresentation.Body(goal_code = Some("Propose Presentation")),
    attachments = Nil
  )

  val testSuite = suite("CRUD operations")(
    test("createPresentationRecord creates a new record in DB") {
      for {
        repo <- ZIO.service[PresentationRepository]
        record <- presentationRecord
        _ <- repo.createPresentationRecord(record)
        result <- repo.findPresentationRecord(record.id)
      } yield assert(result)(isSome)
    },
    test("createPresentationRecord correctly read and write on non-null connectionId") {
      for {
        repo <- ZIO.service[PresentationRepository]
        record <- presentationRecord.map(_.copy(connectionId = Some("connectionId")))
        _ <- repo.createPresentationRecord(record)
        readRecord <- repo.findPresentationRecord(record.id)
      } yield assert(readRecord)(isSome(equalTo(record)))
    },
    test("getPresentationRecord correctly returns an existing record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        record <- repo.findPresentationRecord(bRecord.id)
      } yield assertTrue(record.contains(bRecord))
    },
    test("getPresentationRecord returns None for an unknown record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        record <- repo.findPresentationRecord(DidCommID())
      } yield assertTrue(record.isEmpty)
    },
    test("getPresentationRecord returns all records") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        records <- repo.getPresentationRecords(false)
      } yield {
        assertTrue(records.size == 2) &&
        assertTrue(records.contains(aRecord)) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("getPresentationRecordByThreadId correctly returns an existing thid") {
      for {
        repo <- ZIO.service[PresentationRepository]
        thid = DidCommID()
        aRecord <- presentationRecord.map(_.copy(thid = thid))
        bRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        record <- repo.findPresentationRecordByThreadId(thid)
      } yield assertTrue(record.contains(aRecord))
    },
    test("getPresentationRecordByThreadId returns nothing for an unknown thid") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        record <- repo.findPresentationRecordByThreadId(DidCommID())
      } yield assertTrue(record.isEmpty)
    },
    test("getPresentationRecordsByStates returns valid records") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        cRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        _ <- repo.createPresentationRecord(cRecord)
        _ <- repo.updatePresentationRecordProtocolState(
          aRecord.id,
          ProtocolState.RequestPending,
          ProtocolState.RequestSent
        )
        _ <- repo.updatePresentationRecordProtocolState(
          cRecord.id,
          ProtocolState.RequestPending,
          ProtocolState.PresentationReceived
        )
        pendingRecords <- repo.getPresentationRecordsByStates(
          ignoreWithZeroRetries = true,
          limit = 10,
          ProtocolState.RequestPending
        )
        otherRecords <- repo.getPresentationRecordsByStates(
          ignoreWithZeroRetries = true,
          limit = 10,
          ProtocolState.RequestSent,
          ProtocolState.PresentationReceived
        )
      } yield {
        assertTrue(pendingRecords.size == 1) &&
        assertTrue(pendingRecords.contains(bRecord)) &&
        assertTrue(otherRecords.size == 2) &&
        assertTrue(otherRecords.exists(_.id == aRecord.id)) &&
        assertTrue(otherRecords.exists(_.id == cRecord.id))
      }
    },
    test("getPresentationRecordsByStates returns an empty list if 'states' parameter is empty") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        cRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        _ <- repo.createPresentationRecord(cRecord)
        records <- repo.getPresentationRecordsByStates(ignoreWithZeroRetries = true, limit = 10)
      } yield {
        assertTrue(records.isEmpty)
      }
    },
    test("updatePresentationWithCredentialsToUse updates the record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        cRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        _ <- repo.createPresentationRecord(cRecord)
        _ <- repo.updatePresentationWithCredentialsToUse(
          aRecord.id,
          Some(Seq("credential1", "credential2")),
          ProtocolState.PresentationPending
        )
        records <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.exists(_.credentialsToUse.contains(Seq("credential1", "credential2"))))
      }
    },
    test("updatePresentationWithCredentialsToUse updates the record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        bRecord <- presentationRecord
        cRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        _ <- repo.createPresentationRecord(bRecord)
        _ <- repo.createPresentationRecord(cRecord)
        anoncredCredentialProofs = AnoncredCredentialProofsV1(
          List(AnoncredCredentialProofV1("credential1", Seq("requestedAttribute"), Seq("requestedPredicate")))
        )
        anoncredCredentialProofsJson <- ZIO.fromEither(
          AnoncredCredentialProofsV1.schemaSerDes.serialize(anoncredCredentialProofs)
        )
        _ <- repo.updateAnoncredPresentationWithCredentialsToUse(
          aRecord.id,
          Some(AnoncredCredentialProofsV1.version),
          Some(anoncredCredentialProofsJson),
          ProtocolState.PresentationPending
        )
        records <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.exists(_.anoncredCredentialsToUse.contains(anoncredCredentialProofsJson))) &&
        assertTrue(records.exists(_.anoncredCredentialsToUseJsonSchemaId.contains(AnoncredCredentialProofsV1.version)))
      }
    },
    test("updateCredentialRecordProtocolState updates the record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record <- repo.findPresentationRecord(aRecord.id)
        _ <- repo.updatePresentationRecordProtocolState(
          aRecord.id,
          ProtocolState.RequestPending,
          ProtocolState.RequestSent
        )
        updatedRecord <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record.get.protocolState == ProtocolState.RequestPending) &&
        assertTrue(updatedRecord.get.protocolState == ProtocolState.RequestSent)
      }
    },
    test("updateCredentialRecordProtocolState doesn't update the record for invalid from state") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record <- repo.findPresentationRecord(aRecord.id)
        exit <- repo
          .updatePresentationRecordProtocolState(
            aRecord.id,
            ProtocolState.PresentationPending,
            ProtocolState.RequestSent
          )
          .exit
      } yield assert(exit)(dies(hasMessage(equalTo("Unexpected affected row count: 0"))))
    },
    test("updateWithRequestPresentation updates record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record <- repo.findPresentationRecord(aRecord.id)
        request = requestPresentation
        _ <- repo.updateWithRequestPresentation(
          aRecord.id,
          request,
          ProtocolState.RequestPending
        )
        updatedRecord <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record.get.requestPresentationData.isEmpty) &&
        assertTrue(updatedRecord.get.requestPresentationData.contains(request))
      }
    },
    test("updateWithPresentation updates record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record <- repo.findPresentationRecord(aRecord.id)
        presentation = Presentation.makePresentationFromRequest(requestPresentation.makeMessage)
        _ <- repo.updateWithPresentation(
          aRecord.id,
          presentation,
          ProtocolState.PresentationPending
        )
        updatedRecord <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record.get.presentationData.isEmpty) &&
        assertTrue(updatedRecord.get.presentationData.contains(presentation))
      }
    },
    test("updateWithProposePresentation updates record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record <- repo.findPresentationRecord(aRecord.id)
        request = proposePresentation
        _ <- repo.updateWithProposePresentation(
          aRecord.id,
          request,
          ProtocolState.ProposalPending
        )
        updatedRecord <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record.get.proposePresentationData.isEmpty) &&
        assertTrue(updatedRecord.get.proposePresentationData.contains(request))
      }
    },
    test("updateFail (fail one retry) updates record") {
      val failReason = Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "Just to test"))
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        tmp <- repo.createPresentationRecord(aRecord)
        record0 <- repo.findPresentationRecord(aRecord.id)
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "Just to test"))
        ) // TEST
        updatedRecord1 <- repo.findPresentationRecord(aRecord.id)
        _ <- repo.updatePresentationRecordProtocolState(
          aRecord.id,
          ProtocolState.RequestPending,
          ProtocolState.RequestSent
        )
        updatedRecord2 <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record0.isDefined) &&
        assertTrue(record0.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord1.get.metaRetries == (maxRetries - 1)) &&
        assertTrue(updatedRecord1.get.metaLastFailure == failReason) &&
        assertTrue(updatedRecord1.get.metaNextRetry.isDefined) &&
        assertTrue(updatedRecord2.get.metaNextRetry.isDefined) &&
        assertTrue(updatedRecord2.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord2.get.metaLastFailure.isEmpty)
      }
    },
    test("updateFail (fail all retry) updates record") {
      for {
        repo <- ZIO.service[PresentationRepository]
        aRecord <- presentationRecord
        _ <- repo.createPresentationRecord(aRecord)
        record0 <- repo.findPresentationRecord(aRecord.id)
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "1 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "2 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "3 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "4 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "5 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "6 - Just to test"))
        )
        // The 6 retry should not happen since the max retries is 5
        // (but should also not have an effect other that update the error message)
        updatedRecord1 <- repo.findPresentationRecord(aRecord.id)
      } yield {
        assertTrue(record0.isDefined) &&
        assertTrue(record0.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord1.get.metaRetries == 0) && // assume the max retries is 5
        assertTrue(updatedRecord1.get.metaNextRetry.isEmpty) &&
        assertTrue(
          updatedRecord1.get.metaLastFailure.contains(
            FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "6 - Just to test")
          )
        )
      }
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

  val multitenantTestSuite = suite("muilti-tenant CRUD operation")(
    test("do not see PresentationRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[PresentationRepository]
        record1 <- presentationRecord.provide(wallet1)
        record2 <- presentationRecord.provide(wallet2)
        _ <- repo.createPresentationRecord(record1).provide(wallet1)
        _ <- repo.createPresentationRecord(record2).provide(wallet2)
        ownWalletRecords1 <- repo.getPresentationRecords(false).provide(wallet1)
        ownWalletRecords2 <- repo.getPresentationRecords(false).provide(wallet2)
        crossWalletRecordById <- repo.findPresentationRecord(record2.id).provide(wallet1)
        crossWalletRecordByThid <- repo.findPresentationRecordByThreadId(record2.thid).provide(wallet1)
      } yield assert(ownWalletRecords1)(hasSameElements(Seq(record1))) &&
        assert(ownWalletRecords2)(hasSameElements(Seq(record2))) &&
        assert(crossWalletRecordById)(isNone) &&
        assert(crossWalletRecordByThid)(isNone)
    },
    test("unable to update updatePresentationWithCredentialsToUse outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      val newState = PresentationRecord.ProtocolState.PresentationVerified
      for {
        repo <- ZIO.service[PresentationRepository]
        record1 <- presentationRecord.provide(wallet1)
        record2 <- presentationRecord.provide(wallet2)
        _ <- repo.createPresentationRecord(record1).provide(wallet1)
        exit <- repo.updatePresentationWithCredentialsToUse(record2.id, Option(Nil), newState).provide(wallet2).exit
      } yield assert(exit)(dies(hasMessage(equalTo("Unexpected affected row count: 0"))))
    },
    test("updateAfterFail PresentationRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[PresentationRepository]
        record1 <- presentationRecord.provide(wallet1)
        record2 <- presentationRecord.provide(wallet2)
        _ <- repo.createPresentationRecord(record1).provide(wallet1)
        exit <- repo
          .updateAfterFail(
            record2.id,
            Some(FailureInfo("PresentationRepositorySpecSuite", StatusCode(999), "fail reason"))
          )
          .exit
      } yield assert(exit)(dies(hasMessage(equalTo("Unexpected affected row count: 0"))))
    },
    test("unable to updatePresentationRecordProtocolState PresentationRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      val newState = PresentationRecord.ProtocolState.PresentationVerified
      for {
        repo <- ZIO.service[PresentationRepository]
        record1 <- presentationRecord.provide(wallet1)
        record2 <- presentationRecord.provide(wallet2)
        _ <- repo.createPresentationRecord(record1).provide(wallet1)
        exit <- repo
          .updatePresentationRecordProtocolState(record2.id, record1.protocolState, newState)
          .provide(wallet2)
          .exit
      } yield assert(exit)(dies(hasMessage(equalTo("Unexpected affected row count: 0"))))
    },
    test("getPresentationRecordsByStatesForAllWallets  should return all the records") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[PresentationRepository]
        record1 <- presentationRecord.provide(wallet1)
        record2 <- presentationRecord.provide(wallet2)
        _ <- repo.createPresentationRecord(record1).provide(wallet1)
        _ <- repo.createPresentationRecord(record2).provide(wallet2)
        _ <- repo
          .updatePresentationRecordProtocolState(
            record1.id,
            ProtocolState.RequestPending,
            ProtocolState.RequestSent
          )
          .provide(wallet1)
        _ <- repo
          .updatePresentationRecordProtocolState(
            record2.id,
            ProtocolState.RequestPending,
            ProtocolState.PresentationReceived
          )
          .provide(wallet2)
        allRecords <- repo.getPresentationRecordsByStatesForAllWallets(
          ignoreWithZeroRetries = true,
          limit = 10,
          ProtocolState.RequestSent,
          ProtocolState.PresentationReceived
        )
      } yield assertTrue(allRecords.size == 2) &&
        assertTrue(allRecords.exists(_.id == record1.id)) &&
        assertTrue(allRecords.exists(_.id == record2.id))
    },
  )
}
