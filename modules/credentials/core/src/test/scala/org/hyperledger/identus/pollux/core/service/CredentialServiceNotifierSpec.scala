package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.event.notification.{EventNotificationService, EventNotificationServiceImpl}
import org.hyperledger.identus.mercury.protocol.issuecredential.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.ProtocolState
import org.hyperledger.identus.pollux.core.repository.CredentialRepositoryInMemory
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.mock.{Expectation, MockSpecDefault}
import zio.test.*

import java.time.Instant

object CredentialServiceNotifierSpec extends MockSpecDefault with CredentialServiceSpecHelper {

  private val issueCredentialRecord = IssueCredentialRecord(
    DidCommID(),
    Instant.now,
    None,
    DidCommID(),
    None,
    None,
    None,
    CredentialFormat.JWT,
    None,
    IssueCredentialRecord.Role.Issuer,
    None,
    None,
    None,
    None,
    ProtocolState.OfferPending,
    None,
    None,
    None,
    None,
    None,
    None,
    5,
    None,
    None
  )

  private val issuerExpectations =
    MockCredentialService.CreateJWTIssueCredentialRecord(
      assertion = Assertion.anything,
      result = Expectation.value(issueCredentialRecord)
    ) ++
      MockCredentialService.MarkOfferSent(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.OfferSent))
      ) ++
      MockCredentialService.ReceiveCredentialRequest(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.RequestReceived))
      ) ++
      MockCredentialService.AcceptCredentialRequest(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.CredentialPending))
      ) ++
      MockCredentialService.GenerateJWTCredential(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.CredentialGenerated))
      ) ++
      MockCredentialService.MarkCredentialSent(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.CredentialSent))
      )

  private val holderExpectations =
    MockCredentialService.ReceiveCredentialOffer(
      assertion = Assertion.anything,
      result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.OfferReceived))
    ) ++ MockCredentialService.AcceptCredentialOffer(
      assertion = Assertion.anything,
      result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.RequestPending))
    ) ++
      MockCredentialService.GenerateJWTCredentialRequest(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.RequestGenerated))
      ) ++
      MockCredentialService.MarkRequestSent(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.RequestSent))
      ) ++
      MockCredentialService.ReceiveCredentialIssue(
        assertion = Assertion.anything,
        result = Expectation.value(issueCredentialRecord.copy(protocolState = ProtocolState.CredentialReceived))
      )

  override def spec: Spec[TestEnvironment & Scope, Any] = {
    suite("CredentialServiceWithEventNotificationImpl")(
      test("Happy flow generates relevant events on issuer side") {
        for {
          svc <- ZIO.service[CredentialService]
          ens <- ZIO.service[EventNotificationService]

          offerCreatedRecord <- svc.createJWTIssueCredentialRecord()
          issuerRecordId = offerCreatedRecord.id
          _ <- svc.markOfferSent(issuerRecordId)
          _ <- svc.receiveCredentialRequest(requestCredential())
          _ <- svc.acceptCredentialRequest(issuerRecordId)
          _ <- svc.generateJWTCredential(issuerRecordId, "status-list-registry")
          _ <- svc.markCredentialSent(issuerRecordId)
          consumer <- ens.consumer[IssueCredentialRecord]("Issue")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 6) &&
          assertTrue(events.head.data.protocolState == ProtocolState.OfferPending) &&
          assertTrue(events(1).data.protocolState == ProtocolState.OfferSent) &&
          assertTrue(events(2).data.protocolState == ProtocolState.RequestReceived) &&
          assertTrue(events(3).data.protocolState == ProtocolState.CredentialPending) &&
          assertTrue(events(4).data.protocolState == ProtocolState.CredentialGenerated) &&
          assertTrue(events(5).data.protocolState == ProtocolState.CredentialSent)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          CredentialRepositoryInMemory.layer ++
            issuerExpectations.toLayer
        ) >>> CredentialServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("Happy flow generates relevant events on the holder side") {
        for {
          svc <- ZIO.service[CredentialService]
          ens <- ZIO.service[EventNotificationService]

          offerReceivedRecord <- svc.receiveCredentialOffer(offerCredential())
          holderRecordId = offerReceivedRecord.id
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          _ <- svc.acceptCredentialOffer(holderRecordId, Some(subjectId), None)
          _ <- svc.generateJWTCredentialRequest(offerReceivedRecord.id)
          _ <- svc.markRequestSent(holderRecordId)
          _ <- svc.receiveCredentialIssue(issueCredential())
          consumer <- ens.consumer[IssueCredentialRecord]("Issue")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 5) &&
          assertTrue(events.head.data.protocolState == ProtocolState.OfferReceived) &&
          assertTrue(events(1).data.protocolState == ProtocolState.RequestPending) &&
          assertTrue(events(2).data.protocolState == ProtocolState.RequestGenerated) &&
          assertTrue(events(3).data.protocolState == ProtocolState.RequestSent) &&
          assertTrue(events(4).data.protocolState == ProtocolState.CredentialReceived)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          CredentialRepositoryInMemory.layer ++
            holderExpectations.toLayer
        ) >>> CredentialServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      )
    )
  }

}
