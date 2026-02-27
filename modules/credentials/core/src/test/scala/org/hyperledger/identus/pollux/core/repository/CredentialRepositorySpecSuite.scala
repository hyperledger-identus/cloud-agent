package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.issuecredential.{IssueCredential, RequestCredential}
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.*
import org.hyperledger.identus.shared.models.*
import zio.{Exit, ZIO, ZLayer}
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID

object CredentialRepositorySpecSuite {
  val maxRetries = 5 // TODO Move to config

  private def issueCredentialRecord(credentialFormat: CredentialFormat) = IssueCredentialRecord(
    id = DidCommID(),
    createdAt = Instant.now,
    updatedAt = None,
    thid = DidCommID(),
    schemaUris = None,
    credentialDefinitionId = None,
    credentialDefinitionUri = None,
    credentialFormat = credentialFormat,
    invitation = None,
    role = IssueCredentialRecord.Role.Issuer,
    subjectId = None,
    keyId = None,
    validityPeriod = None,
    automaticIssuance = None,
    protocolState = IssueCredentialRecord.ProtocolState.OfferPending,
    offerCredentialData = None,
    requestCredentialData = None,
    anonCredsRequestMetadata = None,
    issueCredentialData = None,
    issuedCredentialRaw = None,
    issuingDID = None,
    metaRetries = maxRetries,
    metaNextRetry = Some(Instant.now()),
    metaLastFailure = None,
  ).withTruncatedTimestamp()

  private def requestCredential = RequestCredential(
    from = DidId("did:prism:aaa"),
    to = DidId("did:prism:bbb"),
    thid = Some(UUID.randomUUID.toString),
    body = RequestCredential.Body(goal_code = Some("credential issuance")),
    attachments = Nil
  )

  val testSuite = suite("CRUD operations")(
    test("createIssueCredentialRecord creates a new record in DB") {
      for {
        repo <- ZIO.service[CredentialRepository]
        record = issueCredentialRecord(CredentialFormat.JWT)
        result <- repo.create(record)
      } yield assertTrue(result == ())
    },
    test("createIssueCredentialRecord prevents creation of 2 records with the same thid") {
      for {
        repo <- ZIO.service[CredentialRepository]
        thid = DidCommID()
        aRecord = issueCredentialRecord(CredentialFormat.JWT).copy(thid = thid)
        bRecord = issueCredentialRecord(CredentialFormat.JWT).copy(thid = thid)
        _ <- repo.create(aRecord)
        res <- repo.create(bRecord).exit
      } yield assert(res)(dies(anything))
    },
    test("createIssueCredentialRecord correctly read and write on non-null issuingDID") {
      for {
        repo <- ZIO.service[CredentialRepository]
        issuingDID <- ZIO.fromEither(PrismDID.buildCanonicalFromSuffix("0" * 64))
        record = issueCredentialRecord(CredentialFormat.JWT).copy(issuingDID = Some(issuingDID))
        _ <- repo.create(record)
        res <- repo.findById(record.id)
      } yield assert(res)(isSome(equalTo(record)))
    },
    test("getIssueCredentialRecord correctly returns an existing record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        record <- repo.findById(bRecord.id)
      } yield assertTrue(record.contains(bRecord))
    },
    test("getIssuanceCredentialRecord returns None for an unknown record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        record <- repo.findById(DidCommID())
      } yield assertTrue(record.isEmpty)
    },
    test("getIssuanceCredentialRecord returns all records") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        records <- repo.findAll(false).map(_._1)
      } yield {
        assertTrue(records.size == 2) &&
        assertTrue(records.contains(aRecord)) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("getIssuanceCredentialRecord returns records with offset") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        records <- repo.findAll(false, offset = Some(1)).map(_._1)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("getIssuanceCredentialRecord returns records with limit") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        records <- repo.findAll(false, limit = Some(1)).map(_._1)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.contains(aRecord))
      }
    },
    test("getIssuanceCredentialRecord returns records with offset and limit") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        cRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        _ <- repo.create(cRecord)
        records <- repo
          .findAll(false, offset = Some(1), limit = Some(1))
          .map(_._1)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("deleteIssueCredentialRecord deletes an exsiting record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        _ <- repo.deleteById(aRecord.id)
        records <- repo.findAll(false).map(_._1)
      } yield {
        assertTrue(records.size == 1) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("deleteIssueCredentialRecord does nothing for an unknown record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        res <- repo.deleteById(DidCommID()).exit
        records <- repo.findAll(false).map(_._1)
      } yield {
        assertTrue(records.size == 2) &&
        assertTrue(records.contains(aRecord)) &&
        assert(res)(dies(isSubtype[RuntimeException](anything))) &&
        assertTrue(records.contains(bRecord))
      }
    },
    test("getIssueCredentialRecordByThreadId correctly returns an existing thid") {
      for {
        repo <- ZIO.service[CredentialRepository]
        thid = DidCommID()
        aRecord = issueCredentialRecord(CredentialFormat.JWT).copy(thid = thid)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        record <- repo.findByThreadId(thid, false)
      } yield assertTrue(record.contains(aRecord))
    },
    test("getIssueCredentialRecordByThreadId returns nothing for an unknown thid") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        record <- repo.findByThreadId(DidCommID(), false)
      } yield assertTrue(record.isEmpty)
    },
    test("getIssueCredentialRecordsByStates returns valid records") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        cRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        _ <- repo.create(cRecord)
        _ <- repo.updateProtocolState(aRecord.id, ProtocolState.OfferPending, ProtocolState.OfferSent)
        _ <- repo.updateProtocolState(cRecord.id, ProtocolState.OfferPending, ProtocolState.CredentialGenerated)
        pendingRecords <- repo.findByStates(ignoreWithZeroRetries = true, limit = 10, ProtocolState.OfferPending)
        otherRecords <- repo.findByStates(
          ignoreWithZeroRetries = true,
          limit = 10,
          ProtocolState.OfferSent,
          ProtocolState.CredentialGenerated
        )
      } yield {
        assertTrue(pendingRecords.size == 1) &&
        assertTrue(pendingRecords.contains(bRecord)) &&
        assertTrue(otherRecords.size == 2) &&
        assertTrue(otherRecords.exists(_.id == aRecord.id)) &&
        assertTrue(otherRecords.exists(_.id == cRecord.id))
      }
    },
    test("getIssueCredentialRecordsByStates returns an empty list if 'states' parameter is empty") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        cRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        _ <- repo.create(cRecord)
        records <- repo.findByStates(ignoreWithZeroRetries = true, limit = 10)
      } yield {
        assertTrue(records.isEmpty)
      }
    },
    test("getValidIssuedCredentials returns valid records") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        bRecord = issueCredentialRecord(CredentialFormat.JWT)
        cRecord = issueCredentialRecord(CredentialFormat.JWT)
        dRecord = issueCredentialRecord(CredentialFormat.AnonCreds)
        _ <- repo.create(aRecord)
        _ <- repo.create(bRecord)
        _ <- repo.create(cRecord)
        _ <- repo.create(dRecord)
        issueCredential <- ZIO.fromEither(
          IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage)
        )
        _ <- repo.updateWithIssuedRawCredential(
          aRecord.id,
          issueCredential,
          "RAW_CREDENTIAL_DATA",
          None,
          None,
          ProtocolState.CredentialReceived
        )
        _ <- repo.updateWithIssuedRawCredential(
          dRecord.id,
          issueCredential,
          "RAW_CREDENTIAL_DATA",
          None,
          None,
          ProtocolState.CredentialReceived
        )
        records <- repo.findValidIssuedCredentials(Seq(aRecord.id, bRecord.id, dRecord.id))
      } yield {
        assertTrue(records.size == 2) &&
        assertTrue(
          records.contains(
            ValidIssuedCredentialRecord(
              dRecord.id,
              Some("RAW_CREDENTIAL_DATA"),
              CredentialFormat.JWT,
              aRecord.subjectId,
              aRecord.keyId
            )
          )
        )
        assertTrue(
          records.contains(
            ValidIssuedCredentialRecord(
              dRecord.id,
              Some("RAW_CREDENTIAL_DATA"),
              CredentialFormat.AnonCreds,
              aRecord.subjectId,
              aRecord.keyId
            )
          )
        )
      }
    },
    test("updateCredentialRecordProtocolState updates the record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        record <- repo.findById(aRecord.id)
        _ <- repo.updateProtocolState(aRecord.id, ProtocolState.OfferPending, ProtocolState.OfferSent)
        updatedRecord <- repo.findById(aRecord.id)
      } yield {
        assertTrue(record.get.protocolState == ProtocolState.OfferPending) &&
        assertTrue(updatedRecord.get.protocolState == ProtocolState.OfferSent)
      }
    },
    test("updateCredentialRecordProtocolState doesn't update the record for invalid from state") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        record <- repo.getById(aRecord.id)
        res <- repo.updateProtocolState(aRecord.id, ProtocolState.RequestPending, ProtocolState.RequestSent).exit
        updatedRecord <- repo.getById(aRecord.id)
      } yield {
        assertTrue(record.protocolState == ProtocolState.OfferPending) &&
        assert(res)(dies(anything)) &&
        assertTrue(updatedRecord.protocolState == ProtocolState.OfferPending)
      }
    },
    test("updateWithRequestCredential updates record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        record <- repo.findById(aRecord.id)
        request = requestCredential
        _ <- repo.updateWithJWTRequestCredential(aRecord.id, request, ProtocolState.RequestPending)
        updatedRecord <- repo.findById(aRecord.id)
      } yield {
        assertTrue(record.get.requestCredentialData.isEmpty) &&
        assertTrue(updatedRecord.get.requestCredentialData.contains(request))
      }
    },
    test("updateWithIssueCredential updates record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        record <- repo.findById(aRecord.id)
        issueCredential <- ZIO.fromEither(
          IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage)
        )
        _ <- repo.updateWithIssueCredential(aRecord.id, issueCredential, ProtocolState.CredentialPending)
        updatedRecord <- repo.findById(aRecord.id)
      } yield {
        assertTrue(record.get.issueCredentialData.isEmpty) &&
        assertTrue(updatedRecord.get.issueCredentialData.contains(issueCredential))
      }
    },
    test("updateWithIssuedRawCredential updates record") {
      for {
        repo <- ZIO.service[CredentialRepository]
        aRecord = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(aRecord)
        record <- repo.findById(aRecord.id)
        issueCredential <- ZIO.fromEither(
          IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage)
        )
        _ <- repo.updateWithIssuedRawCredential(
          aRecord.id,
          issueCredential,
          "RAW_CREDENTIAL_DATA",
          Some(List("schemaUri")),
          Some("credentialDefinitionUri"),
          ProtocolState.CredentialReceived
        )
        updatedRecord <- repo.findById(aRecord.id)
      } yield {
        assertTrue(record.get.issueCredentialData.isEmpty) &&
        assertTrue(updatedRecord.get.issueCredentialData.contains(issueCredential)) &&
        assertTrue(updatedRecord.get.issuedCredentialRaw.contains("RAW_CREDENTIAL_DATA"))
        assertTrue(updatedRecord.get.credentialDefinitionUri.contains("credentialDefinitionUri"))
        assertTrue(updatedRecord.get.schemaUris.getOrElse(List.empty).contains("schemaUri"))
      }
    },
    test("updateFail (fail one retry) updates record") {
      val aRecord = issueCredentialRecord(CredentialFormat.JWT)

      val failReason = FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "Just to test")
      for {
        repo <- ZIO.service[CredentialRepository]
        _ <- repo.create(aRecord)
        record0 <- repo.findById(aRecord.id)
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "Just to test"))
        )
        updatedRecord1 <- repo.findById(aRecord.id)
        _ <- repo.updateProtocolState(aRecord.id, ProtocolState.OfferPending, ProtocolState.OfferSent)
        updatedRecord2 <- repo.findById(aRecord.id)
      } yield {
        assertTrue(record0.isDefined) &&
        assertTrue(record0.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord1.get.metaRetries == (maxRetries - 1)) &&
        assertTrue(updatedRecord1.get.metaLastFailure.get == failReason) &&
        assertTrue(updatedRecord1.get.metaNextRetry.isDefined) &&
        // continues to work normally after retry
        assertTrue(updatedRecord2.get.metaNextRetry.isDefined) &&
        assertTrue(updatedRecord2.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord2.get.metaLastFailure == None)
      }
    },
    test("updateFail (fail all retry) updates record") {
      val aRecord = issueCredentialRecord(CredentialFormat.JWT)

      for {
        repo <- ZIO.service[CredentialRepository]
        _ <- repo.create(aRecord)
        record0 <- repo.findById(aRecord.id)
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "1 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "2 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "3 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "4 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "5 - Just to test"))
        )
        _ <- repo.updateAfterFail(
          aRecord.id,
          Some(FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "6 - Just to test"))
        )
        // The 6 retry should not happen since the max retries is 5
        // (but should also not have an effect other that update the error message)
        updatedRecord1 <- repo.findById(aRecord.id)
      } yield {

        assertTrue(record0.isDefined) &&
        assertTrue(record0.get.metaRetries == maxRetries) &&
        assertTrue(updatedRecord1.get.metaRetries == 0) && // assume the max retries is 5
        assertTrue(updatedRecord1.get.metaNextRetry.isEmpty) &&
        assertTrue(
          updatedRecord1.get.metaLastFailure == Some(
            FailureInfo("CredentialRepositorySpecSuite", StatusCode(999), "6 - Just to test")
          )
        )

      }
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

  val multitenantTestSuite = suite("multi-tenant CRUD operations")(
    test("do not see IssueCredentialRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[CredentialRepository]
        record1 = issueCredentialRecord(CredentialFormat.JWT)
        record2 = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(record1).provide(wallet1)
        _ <- repo.create(record2).provide(wallet2)
        ownWalletRecords1 <- repo.findAll(false).provide(wallet1)
        ownWalletRecords2 <- repo.findAll(false).provide(wallet2)
        crossWalletRecordById <- repo.findById(record2.id).provide(wallet1)
        crossWalletRecordByThid <- repo.findByThreadId(record2.thid, false).provide(wallet1)
      } yield assert(ownWalletRecords1._1)(hasSameElements(Seq(record1))) &&
        assert(ownWalletRecords2._1)(hasSameElements(Seq(record2))) &&
        assert(crossWalletRecordById)(isNone) &&
        assert(crossWalletRecordByThid)(isNone)
    },
    test("unable to update IssueCredentialRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      val newState = IssueCredentialRecord.ProtocolState.OfferReceived
      for {
        repo <- ZIO.service[CredentialRepository]
        record1 = issueCredentialRecord(CredentialFormat.JWT)
        record2 = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(record1).provide(wallet1)
        res <- repo.updateWithSubjectId(record2.id, "my-id", Some(KeyId("my-key-id")), newState).provide(wallet2).exit
      } yield assert(res)(dies(isSubtype[RuntimeException](anything)))
    },
    test("unable to delete IssueCredentialRecord outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[CredentialRepository]
        record1 = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(record1).provide(wallet1)
        res <- repo.deleteById(record1.id).provide(wallet2).exit
      } yield assert(res)(dies(isSubtype[RuntimeException](anything)))
    },
    test("getIssueCredentialRecordsByStatesForAllWallets should return all the records") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[CredentialRepository]
        record1 = issueCredentialRecord(CredentialFormat.JWT)
        record2 = issueCredentialRecord(CredentialFormat.JWT)
        _ <- repo.create(record1).provide(wallet1)
        _ <- repo.create(record2).provide(wallet2)
        _ <- repo
          .updateProtocolState(record1.id, ProtocolState.OfferPending, ProtocolState.OfferSent)
          .provide(wallet1)
        _ <- repo
          .updateProtocolState(record2.id, ProtocolState.OfferPending, ProtocolState.CredentialGenerated)
          .provide(wallet2)
        allRecords <- repo.findByStatesForAllWallets(
          ignoreWithZeroRetries = true,
          limit = 10,
          ProtocolState.OfferSent,
          ProtocolState.CredentialGenerated
        )
      } yield assertTrue(allRecords.size == 2) &&
        assertTrue(allRecords.exists(_.id == record1.id)) &&
        assertTrue(allRecords.exists(_.id == record2.id))
    },
  )
}
