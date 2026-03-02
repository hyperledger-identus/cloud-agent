package org.hyperledger.identus.vdr

import org.hyperledger.identus.did.api.{DIDKeySigner, DIDKeySignerError, DIDSigningContext}
import org.hyperledger.identus.did.core.model.did.*
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import org.hyperledger.identus.vdr.VdrServiceError.{DeactivatedDid, MissingVdrKey}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object PrismNodeVdrOperationSignerSpec extends ZIOSpecDefault {

  private val apollo = Apollo.default
  private val keyPair = apollo.secp256k1.generateKeyPair

  private val createOp = PrismDIDOperation.Create(publicKeys = Nil, services = Nil, context = Nil)
  private val testDid = createOp.did

  private class StubDIDKeySigner(
      result: Either[DIDKeySignerError, DIDSigningContext]
  ) extends DIDKeySigner {
    override def resolveSigningKey(
        didKeyId: Option[String],
        defaultKeyId: KeyId,
        maxScan: Int
    ): ZIO[WalletAccessContext, DIDKeySignerError, DIDSigningContext] =
      ZIO.fromEither(result)
  }

  private val walletCtxLayer = ZLayer.succeed(WalletAccessContext(WalletId.random))

  override def spec: Spec[TestEnvironment, Any] =
    suite("PrismNodeVdrOperationSigner")(
      test("signCreate uses default vdr-1 when didKeyId absent") {
        val signer = new PrismNodeVdrOperationSigner(
          new StubDIDKeySigner(Right(DIDSigningContext(testDid, KeyId("vdr-1"), keyPair))),
          defaultVdrKeyId = KeyId("vdr-1"),
          maxDidScan = 10
        )
        for {
          signed <- signer.signCreate("data".getBytes(), didKeyId = None).provideLayer(walletCtxLayer)
        } yield assert(signed.signedWith)(equalTo("vdr-1")) &&
          assert(signed.signature.isEmpty)(isFalse) &&
          assert(signed.operation.isDefined)(isTrue)
      },
      test("returns MissingVdrKey when key not found") {
        val signer = new PrismNodeVdrOperationSigner(
          new StubDIDKeySigner(Left(DIDKeySignerError.KeyNotFound("key not found")))
        )
        for {
          result <- signer.signCreate("data".getBytes(), None).provideLayer(walletCtxLayer).exit
        } yield assert(result)(fails(isSubtype[MissingVdrKey](anything)))
      },
      test("fails with DeactivatedDid when DID is deactivated") {
        val signer = new PrismNodeVdrOperationSigner(
          new StubDIDKeySigner(Left(DIDKeySignerError.DIDDeactivated("deactivated")))
        )
        for {
          result <- signer.signCreate("data".getBytes(), None).provideLayer(walletCtxLayer).exit
        } yield assert(result)(fails(isSubtype[DeactivatedDid](anything)))
      }
    )
}
