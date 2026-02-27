package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.agent.walletapi.service.{WalletManagementService, WalletManagementServiceImpl}
import org.hyperledger.identus.agent.walletapi.sql.{
  JdbcGenericSecretStorage,
  JdbcWalletNonSecretStorage,
  JdbcWalletSecretStorage
}
import org.hyperledger.identus.agent.walletapi.vault.{VaultGenericSecretStorage, VaultWalletSecretStorage}
import org.hyperledger.identus.shared.crypto.ApolloSpecHelper
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.{DBTestUtils, VaultTestContainerSupport}
import zio.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID
import scala.util.Try

object GenericSecretStorageSpec
    extends ZIOSpecDefault,
      StorageSpecHelper,
      PostgresTestContainerSupport,
      VaultTestContainerSupport,
      ApolloSpecHelper {

  final case class TestSecret(json: Json) // to be moved to pollux?

  given GenericSecret[UUID, TestSecret] = new {
    override def keyPath(id: UUID): String = s"test-secret/${id.toString}"

    override def encodeValue(secret: TestSecret): Json = secret.json

    override def decodeValue(json: Json): Try[TestSecret] = Try(TestSecret(json))
  }

  private def walletManagementServiceLayer =
    ZLayer.makeSome[WalletSecretStorage, WalletManagementService](
      WalletManagementServiceImpl.layer,
      JdbcWalletNonSecretStorage.layer,
      contextAwareTransactorLayer,
      apolloLayer
    )

  override def spec = {
    val jdbcTestSuite = commonSpec("JdbcGenericSecretStorage")
      .provide(
        JdbcWalletSecretStorage.layer,
        JdbcGenericSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    val vaultTestSuite = commonSpec("VaultGenericSecretStorage")
      .provide(
        VaultWalletSecretStorage.layer,
        VaultGenericSecretStorage.layer(useSemanticPath = true),
        pgContainerLayer,
        vaultKvClientLayer(),
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    val vaultFsTestSuite = commonSpec("VaultGenericSecretStorage - file backend")
      .provide(
        VaultWalletSecretStorage.layer,
        VaultGenericSecretStorage.layer(useSemanticPath = false),
        pgContainerLayer,
        vaultKvClientLayer(useFileBackend = true),
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    val inMemoryTestSuite = commonSpec("InMemoryGenericSecretStorage")
      .provide(
        JdbcWalletSecretStorage.layer,
        GenericSecretStorageInMemory.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        walletManagementServiceLayer,
        ZLayer.succeed(WalletAdministrationContext.Admin())
      )

    suite("GenericSecretStorage")(
      jdbcTestSuite,
      vaultTestSuite,
      vaultFsTestSuite,
      inMemoryTestSuite
    ) @@ TestAspect.sequential
  }

  private def commonSpec(name: String) =
    suite(name)(singleWalletSpec, multiWalletSpec) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

  private val singleWalletSpec = suite("single-wallet")(
    test("insert and get the same item") {
      for {
        storage <- ZIO.service[GenericSecretStorage]
        id = UUID.randomUUID()
        secret = TestSecret(json = Json.Obj("foo" -> Json.Str("bar")))
        _ <- storage.set(id, secret)
        result: Option[TestSecret] <- storage.get(id)
      } yield assert(result)(isSome(equalTo(secret)))
    },
    test("insert item with same path return error") {
      for {
        storage <- ZIO.service[GenericSecretStorage]
        id = UUID.randomUUID()
        secret1 = TestSecret(json = Json.Obj("foo1" -> Json.Str("bar1")))
        secret2 = TestSecret(json = Json.Obj("foo2" -> Json.Str("bar2")))
        _ <- storage.set(id, secret1)
        exit <- storage.set(id, secret2).exit
      } yield assert(exit)(fails(anything))
    },
    test("get non-existing secret return none") {
      for {
        storage <- ZIO.service[GenericSecretStorage]
        id = UUID.randomUUID()
        result <- storage.get(id)
      } yield assert(result)(isNone)
    }
  ).globalWallet

  private val multiWalletSpec = suite("multi-wallet")(
    test("insert item with same path for different wallet do not fail") {
      for {
        walletSvc <- ZIO.service[WalletManagementService]
        walletId1 <- walletSvc.createWallet(Wallet("wallet-1")).map(_.id)
        walletId2 <- walletSvc.createWallet(Wallet("wallet-2")).map(_.id)
        storage <- ZIO.service[GenericSecretStorage]
        id = UUID.randomUUID()
        secret = TestSecret(json = Json.Obj("foo" -> Json.Str("bar")))
        _ <- storage
          .set(id, secret)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        _ <- storage
          .set(id, secret)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        secret1 <- storage
          .get(id)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        secret2 <- storage
          .get(id)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
      } yield assert(secret1)(equalTo(secret2)) && assert(secret1)(isSome)
    },
    test("do no see secret outside of the wallet") {
      for {
        walletSvc <- ZIO.service[WalletManagementService]
        walletId1 <- walletSvc.createWallet(Wallet("wallet-1")).map(_.id)
        walletId2 <- walletSvc.createWallet(Wallet("wallet-2")).map(_.id)
        storage <- ZIO.service[GenericSecretStorage]
        // wallet1 setup
        id1 = UUID.randomUUID()
        secret1 = TestSecret(json = Json.Obj("foo1" -> Json.Str("bar1")))
        _ <- storage
          .set(id1, secret1)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        // wallet2 setup
        id2 = UUID.randomUUID()
        secret2 = TestSecret(json = Json.Obj("foo2" -> Json.Str("bar2")))
        _ <- storage
          .set(id2, secret2)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        // assertions
        ownWallet1 <- storage
          .get(id1)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
        ownWallet2 <- storage
          .get(id2)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        crossWallet1 <- storage
          .get(id1)
          .provide(ZLayer.succeed(WalletAccessContext(walletId2)))
        crossWallet2 <- storage
          .get(id2)
          .provide(ZLayer.succeed(WalletAccessContext(walletId1)))
      } yield assert(ownWallet1)(isSome(equalTo(secret1))) &&
        assert(ownWallet2)(isSome(equalTo(secret2))) &&
        assert(crossWallet1)(isNone) &&
        assert(crossWallet2)(isNone)
    }
  )

}
