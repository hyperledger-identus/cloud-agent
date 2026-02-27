package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState, Wallet}
import org.hyperledger.identus.agent.walletapi.service.{WalletManagementService, WalletManagementServiceImpl}
import org.hyperledger.identus.agent.walletapi.sql.{
  JdbcDIDNonSecretStorage,
  JdbcDIDSecretStorage,
  JdbcWalletNonSecretStorage,
  JdbcWalletSecretStorage
}
import org.hyperledger.identus.agent.walletapi.vault.{VaultDIDSecretStorage, VaultWalletSecretStorage}
import org.hyperledger.identus.castor.core.model.did.PrismDIDOperation
import org.hyperledger.identus.mercury.PeerDID
import org.hyperledger.identus.shared.crypto.{Apollo, ApolloSpecHelper, Ed25519KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletAdministrationContext}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.{DBTestUtils, VaultTestContainerSupport}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object DIDSecretStorageSpec
    extends ZIOSpecDefault,
      StorageSpecHelper,
      PostgresTestContainerSupport,
      VaultTestContainerSupport,
      ApolloSpecHelper {

  private def walletManagementServiceLayer =
    ZLayer.makeSome[WalletSecretStorage, WalletManagementService](
      WalletManagementServiceImpl.layer,
      JdbcWalletNonSecretStorage.layer,
      contextAwareTransactorLayer,
      apolloLayer
    )

  override def spec: Spec[TestEnvironment & Scope, Any] = {
    val jdbcTestSuite = commonSpec("JdbcDIDSecretStorage")
      .provide(
        JdbcDIDNonSecretStorage.layer,
        JdbcDIDSecretStorage.layer,
        JdbcWalletSecretStorage.layer,
        systemTransactorLayer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    val vaultTestSuite = commonSpec("VaultDIDSecretStorage")
      .provide(
        JdbcDIDNonSecretStorage.layer,
        VaultDIDSecretStorage.layer(useSemanticPath = true),
        VaultWalletSecretStorage.layer,
        systemTransactorLayer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        vaultKvClientLayer(),
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    val vaultFsTestSuite = commonSpec("VaultDIDSecretStorage - file backend")
      .provide(
        JdbcDIDNonSecretStorage.layer,
        VaultDIDSecretStorage.layer(useSemanticPath = false),
        VaultWalletSecretStorage.layer,
        systemTransactorLayer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        vaultKvClientLayer(useFileBackend = true),
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    suite("DIDSecretStorage")(
      jdbcTestSuite,
      vaultTestSuite,
      vaultFsTestSuite,
    ) @@ TestAspect.sequential
  }

  private def commonSpec(name: String) =
    suite(name)(singleWalletSpec, multiWalletSpec) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

  private val singleWalletSpec = suite("single-wallet")(
    test("insert and get the same key for OctetKeyPair") {
      for {
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        peerDID = PeerDID.makePeerDid()
        _ <- nonSecretStorage.createPeerDIDRecord(peerDID.did)
        n1 <- secretStorage.insertKey(peerDID.did, KeyId("agreement"), peerDID.jwkForKeyAgreement)
        n2 <- secretStorage.insertKey(peerDID.did, KeyId("authentication"), peerDID.jwkForKeyAuthentication)
        key1 <- secretStorage.getKey(peerDID.did, KeyId("agreement"))
        key2 <- secretStorage.getKey(peerDID.did, KeyId("authentication"))
      } yield assert(n1)(equalTo(1)) &&
        assert(n2)(equalTo(1)) &&
        assert(key1)(isSome(equalTo(peerDID.jwkForKeyAgreement))) &&
        assert(key2)(isSome(equalTo(peerDID.jwkForKeyAuthentication)))
    },
    test("insert same key id return error") {
      for {
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        peerDID = PeerDID.makePeerDid()
        _ <- nonSecretStorage.createPeerDIDRecord(peerDID.did)
        n1 <- secretStorage.insertKey(peerDID.did, KeyId("agreement"), peerDID.jwkForKeyAgreement)
        exit <- secretStorage
          .insertKey(peerDID.did, KeyId("agreement"), peerDID.jwkForKeyAuthentication)
          .exit
        key1 <- secretStorage.getKey(peerDID.did, KeyId("agreement"))
      } yield assert(n1)(equalTo(1)) &&
        assert(exit)(fails(anything)) &&
        assert(key1)(isSome(equalTo(peerDID.jwkForKeyAgreement)))
    },
    test("get non-exist key return none") {
      for {
        secretStorage <- ZIO.service[DIDSecretStorage]
        peerDID = PeerDID.makePeerDid()
        key1 <- secretStorage.getKey(peerDID.did, KeyId("agreement"))
      } yield assert(key1)(isNone)
    },
    test("insert with long DID does not fail") {
      for {
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        peerDID = PeerDID.makePeerDid(serviceEndpoint = Some("http://localhost/" + ("a" * 100)))
        _ <- nonSecretStorage.createPeerDIDRecord(peerDID.did)
        _ <- secretStorage.insertKey(peerDID.did, KeyId("agreement"), peerDID.jwkForKeyAgreement)
        _ <- secretStorage.insertKey(peerDID.did, KeyId("authentication"), peerDID.jwkForKeyAuthentication)
      } yield assertCompletes
    },
    test("insert and get the same key for Prism KeyPair") {
      for {
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        key1 = Apollo.default.ed25519.generateKeyPair
        key2 = Apollo.default.x25519.generateKeyPair
        createOperation = PrismDIDOperation.Create(Nil, Nil, Nil)
        did = createOperation.did
        state = ManagedDIDState(createOperation, 0, PublicationState.Created())
        _ <- nonSecretStorage.insertManagedDID(did, state, Map.empty, Map.empty)
        _ <- secretStorage.insertPrismDIDKeyPair(did, KeyId("key-1"), createOperation.toAtalaOperationHash, key1)
        _ <- secretStorage.insertPrismDIDKeyPair(did, KeyId("key-2"), createOperation.toAtalaOperationHash, key2)
        getKey1 <- secretStorage
          .getPrismDIDKeyPair[Ed25519KeyPair](did, KeyId("key-1"), createOperation.toAtalaOperationHash)
          .some
        getKey2 <- secretStorage
          .getPrismDIDKeyPair[X25519KeyPair](did, KeyId("key-2"), createOperation.toAtalaOperationHash)
          .some
      } yield assert(key1)(equalTo(getKey1)) &&
        assert(key2)(equalTo(getKey2))
    },
    test("insert same key id for Prism KeyPair return error") {
      for {
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        key1 = Apollo.default.ed25519.generateKeyPair
        key2 = Apollo.default.x25519.generateKeyPair
        createOperation = PrismDIDOperation.Create(Nil, Nil, Nil)
        did = createOperation.did
        state = ManagedDIDState(createOperation, 0, PublicationState.Created())
        _ <- nonSecretStorage.insertManagedDID(did, state, Map.empty, Map.empty)
        _ <- secretStorage.insertPrismDIDKeyPair(did, KeyId("key-1"), createOperation.toAtalaOperationHash, key1)
        exit <- secretStorage
          .insertPrismDIDKeyPair(did, KeyId("key-1"), createOperation.toAtalaOperationHash, key2)
          .exit
        getKey1 <- secretStorage
          .getPrismDIDKeyPair[Ed25519KeyPair](did, KeyId("key-1"), createOperation.toAtalaOperationHash)
          .some
      } yield assert(key1)(equalTo(getKey1)) && assert(exit)(dies(anything))
    },
    test("get non-exist Prism KeyPair return None") {
      for {
        secretStorage <- ZIO.service[DIDSecretStorage]
        createOperation = PrismDIDOperation.Create(Nil, Nil, Nil)
        did = createOperation.did
        key1 <- secretStorage
          .getPrismDIDKeyPair[Ed25519KeyPair](did, KeyId("key-1"), createOperation.toAtalaOperationHash)
      } yield assert(key1)(isNone)
    },
  ).globalWallet

  private val multiWalletSpec = suite("multi-wallet")(
    test("do not see peer DID key outside of the wallet") {
      for {
        walletSvc <- ZIO.service[WalletManagementService]
        walletId1 <- walletSvc.createWallet(Wallet("wallet-1")).map(_.id)
        walletId2 <- walletSvc.createWallet(Wallet("wallet-2")).map(_.id)
        nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
        secretStorage <- ZIO.service[DIDSecretStorage]
        // wallet1 setup
        peerDID1 = PeerDID.makePeerDid()
        _ <- nonSecretStorage
          .createPeerDIDRecord(peerDID1.did)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        _ <- secretStorage
          .insertKey(peerDID1.did, KeyId("key-1"), peerDID1.jwkForKeyAgreement)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        // wallet2 setup
        peerDID2 = PeerDID.makePeerDid()
        _ <- nonSecretStorage
          .createPeerDIDRecord(peerDID2.did)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        _ <- secretStorage
          .insertKey(peerDID2.did, KeyId("key-1"), peerDID2.jwkForKeyAgreement)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        // assertions
        ownWallet1 <- secretStorage
          .getKey(peerDID1.did, KeyId("key-1"))
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        ownWallet2 <- secretStorage
          .getKey(peerDID2.did, KeyId("key-1"))
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        crossWallet1 <- secretStorage
          .getKey(peerDID1.did, KeyId("key-1"))
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        crossWallet2 <- secretStorage
          .getKey(peerDID2.did, KeyId("key-1"))
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
      } yield assert(ownWallet1)(isSome(equalTo(peerDID1.jwkForKeyAgreement))) &&
        assert(ownWallet2)(isSome(equalTo(peerDID2.jwkForKeyAgreement))) &&
        assert(crossWallet1)(isNone) &&
        assert(crossWallet2)(isNone)
    }
  )

}
