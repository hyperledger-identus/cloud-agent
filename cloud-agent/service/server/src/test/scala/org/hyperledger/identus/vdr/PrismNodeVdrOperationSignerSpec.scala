package org.hyperledger.identus.vdr

import org.hyperledger.identus.agent.vdr.VdrServiceError.MissingVdrKey
import org.hyperledger.identus.agent.walletapi.model.{
  ManagedDIDDetail,
  ManagedDIDState,
  ManagedDIDTemplate,
  PublicationState,
  UpdateManagedDIDAction
}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.shared.crypto.{Apollo, Secp256k1KeyPair}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*
import zio.test.*
import zio.test.Assertion.*

/** Unit coverage for PrismNodeVdrOperationSigner */
object PrismNodeVdrOperationSignerSpec extends ZIOSpecDefault {

  private val apollo = Apollo.default
  private val keyPair = apollo.secp256k1.generateKeyPair

  private val createOp = PrismDIDOperation.Create(publicKeys = Nil, services = Nil, context = Nil)
  private val didState = ManagedDIDState(createOp, didIndex = 0, PublicationState.Created())
  private val didDetail = ManagedDIDDetail(didState.did, didState)

  private class StubManagedDIDService(keys: Map[KeyId, Option[Any]]) extends ManagedDIDService {
    override def nonSecretStorage: DIDNonSecretStorage = throw new NotImplementedError
    override def syncManagedDIDState = ZIO.dieMessage("unused")
    override def syncUnconfirmedUpdateOperations = ZIO.dieMessage("unused")
    override def findDIDKeyPair(did: CanonicalPrismDID, keyId: KeyId) =
      ZIO.succeed(keys.getOrElse(keyId, None).asInstanceOf[Option[Secp256k1KeyPair]])
    override def getManagedDIDState(did: CanonicalPrismDID) = ZIO.succeed(Some(didState))
    override def listManagedDIDPage(offset: Int, limit: Int) = ZIO.succeed((Seq(didDetail), 1))
    override def publishStoredDID(did: CanonicalPrismDID) = ZIO.dieMessage("unused")
    override def createAndStoreDID(didTemplate: ManagedDIDTemplate) = ZIO.dieMessage("unused")
    override def updateManagedDID(did: CanonicalPrismDID, actions: Seq[UpdateManagedDIDAction]) =
      ZIO.dieMessage("unused")
    override def deactivateManagedDID(did: CanonicalPrismDID) = ZIO.dieMessage("unused")
    override def createAndStorePeerDID(serviceEndpoint: java.net.URL) = ZIO.dieMessage("unused")
    override def getPeerDID(didId: org.hyperledger.identus.mercury.model.DidId) =
      ZIO.dieMessage("unused")
  }

  private val walletCtxLayer = ZLayer.succeed(WalletAccessContext(WalletId.random))

  override def spec: Spec[TestEnvironment, Any] =
    suite("PrismNodeVdrOperationSigner")(
      test("signCreate uses default vdr-1 when didKeyId absent") {
        val signer = new PrismNodeVdrOperationSigner(
          new StubManagedDIDService(Map(KeyId("vdr-1") -> Some(keyPair)))
        )
        for {
          signed <- signer.signCreate("data".getBytes(), didKeyId = None).provideLayer(walletCtxLayer)
        } yield assert(signed.signedWith)(equalTo("vdr-1")) &&
          assert(signed.signature.isEmpty)(isFalse) &&
          assert(signed.operation.isDefined)(isTrue)
      },
      test("returns MissingVdrKey when no managed DID exists") {
        val signer = new PrismNodeVdrOperationSigner(new StubManagedDIDService(Map.empty) {
          override def listManagedDIDPage(offset: Int, limit: Int) = ZIO.succeed((Seq.empty, 0))
        })
        for {
          result <- signer.signCreate("data".getBytes(), None).provideLayer(walletCtxLayer).exit
        } yield assert(result)(fails(isSubtype[MissingVdrKey](anything)))
      },
      test("returns MissingVdrKey when key is missing") {
        val signer = new PrismNodeVdrOperationSigner(
          new StubManagedDIDService(Map(KeyId("vdr-1") -> None))
        )
        for {
          result <- signer.signCreate("data".getBytes(), None).provideLayer(walletCtxLayer).exit
        } yield assert(result)(fails(isSubtype[MissingVdrKey](anything)))
      }
    )
}
