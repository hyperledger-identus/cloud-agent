package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.agent.walletapi.model.error.UpdateManagedDIDError
import org.hyperledger.identus.agent.walletapi.model.PublicationState
import org.hyperledger.identus.agent.walletapi.storage.{DIDNonSecretStorage, MockDIDNonSecretStorage}
import org.hyperledger.identus.did.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  PrismDIDOperation,
  VerificationRelationship
}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*
import zio.mock.Expectation
import zio.test.*
import zio.test.Assertion.*

import scala.collection.immutable.ArraySeq

object ValidateInternalKeyRemovalSpec extends ZIOSpecDefault {

  private val createOp = PrismDIDOperation.Create(Nil, Nil, Nil)
  private val did = createOp.did
  private val state = ManagedDIDState(
    createOperation = createOp,
    didIndex = 0,
    publicationState = PublicationState.Published(ArraySeq.empty)
  )
  private val ctx = ZLayer.succeed(WalletAccessContext(WalletId.default))

  private def hd(path: ManagedDIDHdKeyPath) = ManagedDIDKeyMeta.HD(path)

  override def spec = suite("validateInternalKeyRemoval")(
    test("fails when key does not exist") {
      val expectation = MockDIDNonSecretStorage.GetKeyMeta(
        assertion = equalTo((did, KeyId("missing"))),
        result = Expectation.value(None)
      )
      val eff = for {
        nonSecret <- ZIO.service[DIDNonSecretStorage]
        svc = new ManagedDIDServiceImpl(
          didService = null,
          didOpValidator = null,
          secretStorage = null,
          nonSecretStorage = nonSecret,
          walletSecretStorage = null,
          apollo = null
        )
        _ <- svc.validateInternalKeyRemoval(state, Seq(UpdateManagedDIDAction.RemoveInternalKey("missing")))
      } yield ()
      val provided = eff.provide(ctx, expectation)
      assertZIO(provided.exit)(fails(isSubtype[UpdateManagedDIDError.InvalidArgument](anything)))
    },
    test("fails when key is not internal HD (rand/public)") {
      val expectation = MockDIDNonSecretStorage.GetKeyMeta(
        assertion = equalTo((did, KeyId("pub-1"))),
        result = Expectation.value(
          Some(
            ManagedDIDKeyMeta.Rand(
              ManagedDIDRandKeyMeta(VerificationRelationship.Authentication, EllipticCurve.ED25519)
            ) -> Array.emptyByteArray
          )
        )
      )
      val eff = for {
        nonSecret <- ZIO.service[DIDNonSecretStorage]
        svc = new ManagedDIDServiceImpl(null, null, null, nonSecret, null, null)
        _ <- svc.validateInternalKeyRemoval(state, Seq(UpdateManagedDIDAction.RemoveInternalKey("pub-1")))
      } yield ()
      val provided = eff.provide(ctx, expectation)
      assertZIO(provided.exit)(fails(isSubtype[UpdateManagedDIDError.InvalidArgument](anything)))
    },
    test("fails when key usage is not VDR") {
      val path = ManagedDIDHdKeyPath(0, InternalKeyPurpose.Revocation, 0)
      val expectation = MockDIDNonSecretStorage.GetKeyMeta(
        assertion = equalTo((did, KeyId("rev-1"))),
        result = Expectation.value(Some(hd(path) -> Array.emptyByteArray))
      )
      val eff = for {
        nonSecret <- ZIO.service[DIDNonSecretStorage]
        svc = new ManagedDIDServiceImpl(null, null, null, nonSecret, null, null)
        _ <- svc.validateInternalKeyRemoval(state, Seq(UpdateManagedDIDAction.RemoveInternalKey("rev-1")))
      } yield ()
      val provided = eff.provide(ctx, expectation)
      assertZIO(provided.exit)(fails(isSubtype[UpdateManagedDIDError.InvalidArgument](anything)))
    },
    test("succeeds when key is VDR internal") {
      val path = ManagedDIDHdKeyPath(0, InternalKeyPurpose.VDR, 0)
      val expectation = MockDIDNonSecretStorage.GetKeyMeta(
        assertion = equalTo((did, KeyId("vdr-1"))),
        result = Expectation.value(Some(hd(path) -> Array.emptyByteArray))
      )
      val eff = for {
        nonSecret <- ZIO.service[DIDNonSecretStorage]
        svc = new ManagedDIDServiceImpl(null, null, null, nonSecret, null, null)
        _ <- svc.validateInternalKeyRemoval(state, Seq(UpdateManagedDIDAction.RemoveInternalKey("vdr-1")))
      } yield ()
      val provided = eff.provide(ctx, expectation)
      assertZIO(provided.exit)(succeeds(anything))
    }
  )
}
