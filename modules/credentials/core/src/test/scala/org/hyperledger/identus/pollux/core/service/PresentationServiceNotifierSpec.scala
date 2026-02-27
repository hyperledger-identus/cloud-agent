package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.event.notification.{EventNotificationService, EventNotificationServiceImpl}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.presentproof.{
  PresentCredentialRequestFormat,
  Presentation,
  RequestPresentation
}
import org.hyperledger.identus.pollux.core.model.{CredentialFormat, DidCommID, PresentationRecord}
import org.hyperledger.identus.pollux.core.model.PresentationRecord.ProtocolState
import org.hyperledger.identus.pollux.core.repository.PresentationRepositoryInMemory
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.{Scope, ZIO, ZLayer}
import zio.mock.Expectation
import zio.test.{assertTrue, Assertion, Spec, TestEnvironment, ZIOSpecDefault}

import java.time.Instant
import java.util.UUID

object PresentationServiceNotifierSpec extends ZIOSpecDefault with PresentationServiceSpecHelper {

  private val record = PresentationRecord(
    DidCommID(""),
    Instant.now(),
    None,
    DidCommID(""),
    None,
    None,
    PresentationRecord.Role.Verifier,
    ProtocolState.RequestPending,
    CredentialFormat.JWT,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    5,
    None,
    None,
    walletId = WalletId.fromUUID(UUID.randomUUID())
  )

  private val verifierHappyFlowExpectations =
    MockPresentationService.CreateJwtPresentationRecord(
      assertion = Assertion.anything,
      result = Expectation.value(record)
    ) ++
      MockPresentationService.MarkRequestPresentationSent(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationSent))
      ) ++
      MockPresentationService.ReceivePresentation(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationReceived))
      ) ++
      MockPresentationService.MarkPresentationVerified(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationVerified))
      ) ++
      MockPresentationService.AcceptPresentation(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationAccepted))
      )

  private val verifierPresentationVerificationFailedExpectations =
    MockPresentationService.MarkPresentationVerificationFailed(
      assertion = Assertion.anything,
      result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationVerificationFailed))
    )

  private val verifierRejectPresentationExpectations =
    MockPresentationService.RejectPresentation(
      assertion = Assertion.anything,
      result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationRejected))
    )

  private val proverHappyFlowExpectations =
    MockPresentationService.ReceiveRequestPresentation(
      assertion = Assertion.anything,
      result = Expectation.value(record.copy(protocolState = ProtocolState.RequestReceived))
    ) ++
      MockPresentationService.AcceptRequestPresentation(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationAccepted))
      ) ++
      MockPresentationService.MarkPresentationGenerated(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationGenerated))
      ) ++
      MockPresentationService.MarkPresentationSent(
        assertion = Assertion.anything,
        result = Expectation.value(record.copy(protocolState = ProtocolState.PresentationSent))
      )

  private val proverRejectPresentationRequestExpectations =
    MockPresentationService.RejectRequestPresentation(
      assertion = Assertion.anything,
      result = Expectation.value(record.copy(protocolState = ProtocolState.RequestRejected))
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("PresentationServiceWithEventNotificationImpl")(
      test("Happy flow generates relevant events on the verifier side") {
        for {
          svc <- ZIO.service[PresentationService]
          ens <- ZIO.service[EventNotificationService]

          record <- svc.createJwtPresentationRecord(
            DidId(""),
            Some(DidId("")),
            DidCommID(""),
            None,
            Seq.empty,
            None,
            PresentCredentialRequestFormat.JWT,
            None,
            None,
            None
          )
          _ <- svc.markRequestPresentationSent(record.id)
          _ <- svc.receivePresentation(presentation(record.thid.value))
          _ <- svc.markPresentationVerified(record.id)
          _ <- svc.acceptPresentation(record.id)

          consumer <- ens.consumer[PresentationRecord]("Presentation")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 5) &&
          assertTrue(events.head.data.protocolState == ProtocolState.RequestPending) &&
          assertTrue(events(1).data.protocolState == ProtocolState.RequestSent)
          assertTrue(events(2).data.protocolState == ProtocolState.PresentationReceived) &&
          assertTrue(events(3).data.protocolState == ProtocolState.PresentationVerified) &&
          assertTrue(events(4).data.protocolState == ProtocolState.PresentationAccepted)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          PresentationRepositoryInMemory.layer ++
            verifierHappyFlowExpectations.toLayer
        ) >>> PresentationServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("Generates relevant events on presentation verification failed") {
        for {
          svc <- ZIO.service[PresentationService]
          ens <- ZIO.service[EventNotificationService]

          _ <- svc.markPresentationVerificationFailed(DidCommID())

          consumer <- ens.consumer[PresentationRecord]("Presentation")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 1) &&
          assertTrue(events.head.data.protocolState == ProtocolState.PresentationVerificationFailed)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          PresentationRepositoryInMemory.layer ++
            verifierPresentationVerificationFailedExpectations.toLayer
        ) >>> PresentationServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("Generates relevant events on presentation rejected") {
        for {
          svc <- ZIO.service[PresentationService]
          ens <- ZIO.service[EventNotificationService]

          _ <- svc.rejectPresentation(DidCommID())

          consumer <- ens.consumer[PresentationRecord]("Presentation")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 1) &&
          assertTrue(events.head.data.protocolState == ProtocolState.PresentationRejected)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          PresentationRepositoryInMemory.layer ++
            verifierRejectPresentationExpectations.toLayer
        ) >>> PresentationServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("Happy flow generates relevant events on the prover side") {
        for {
          svc <- ZIO.service[PresentationService]
          ens <- ZIO.service[EventNotificationService]

          _ <- svc.receiveRequestPresentation(None, requestPresentation(PresentCredentialRequestFormat.JWT))
          _ <- svc.acceptRequestPresentation(record.id, Seq.empty)
          _ <- svc.markPresentationGenerated(record.id, presentation(record.thid.value))
          _ <- svc.markPresentationSent(record.id)

          consumer <- ens.consumer[PresentationRecord]("Presentation")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 4) &&
          assertTrue(events.head.data.protocolState == ProtocolState.RequestReceived) &&
          assertTrue(events(1).data.protocolState == ProtocolState.PresentationPending)
          assertTrue(events(2).data.protocolState == ProtocolState.PresentationGenerated) &&
          assertTrue(events(3).data.protocolState == ProtocolState.PresentationSent)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          PresentationRepositoryInMemory.layer ++
            proverHappyFlowExpectations.toLayer
        ) >>> PresentationServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("Happy flow generates relevant events on the prover side") {
        for {
          svc <- ZIO.service[PresentationService]
          ens <- ZIO.service[EventNotificationService]

          _ <- svc.rejectRequestPresentation(record.id)

          consumer <- ens.consumer[PresentationRecord]("Presentation")
          events <- consumer.poll(50)
        } yield {
          assertTrue(events.size == 1) &&
          assertTrue(events.head.data.protocolState == ProtocolState.RequestRejected)
        }
      }.provide(
        ZLayer.succeed(50) >>> EventNotificationServiceImpl.layer,
        (
          PresentationRepositoryInMemory.layer ++
            proverRejectPresentationRequestExpectations.toLayer
        ) >>> PresentationServiceNotifier.layer,
        ZLayer.succeed(WalletAccessContext(WalletId.random))
      )
    )
}
