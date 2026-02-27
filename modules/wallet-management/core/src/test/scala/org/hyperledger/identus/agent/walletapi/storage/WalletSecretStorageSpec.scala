package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.{Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.sql.{JdbcWalletNonSecretStorage, JdbcWalletSecretStorage}
import org.hyperledger.identus.agent.walletapi.vault.VaultWalletSecretStorage
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.{DBTestUtils, VaultTestContainerSupport}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object WalletSecretStorageSpec extends ZIOSpecDefault, PostgresTestContainerSupport, VaultTestContainerSupport {

  override def spec = {
    def testSuite(name: String) = suite(name)(
      setWalletSeedSpec
    ) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

    val suite1 = testSuite("jdbc as storage")
      .provide(
        JdbcWalletSecretStorage.layer,
        JdbcWalletNonSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer
      )

    val suite2 = testSuite("jdbc as storage")
      .provide(
        VaultWalletSecretStorage.layer,
        JdbcWalletNonSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        vaultKvClientLayer()
      )

    suite("WalletSecretStorage")(suite1, suite2)
  }

  private def setWalletSeedSpec = suite("setWalletSeed")(
    test("set seed on a new wallet") {
      for {
        storage <- ZIO.service[WalletSecretStorage]
        walletId <- ZIO
          .serviceWithZIO[WalletNonSecretStorage](_.createWallet(Wallet("wallet-1"), Array.emptyByteArray))
          .map(_.id)
        walletAccessCtx = ZLayer.succeed(WalletAccessContext(walletId))
        seed = WalletSeed.fromByteArray(Array.fill[Byte](64)(0)).toOption.get
        seedBefore <- storage.findWalletSeed.provide(walletAccessCtx)
        _ <- storage.setWalletSeed(seed).provide(walletAccessCtx)
        seedAfter <- storage.findWalletSeed.provide(walletAccessCtx)
      } yield assert(seedBefore)(isNone) &&
        assert(seedAfter)(isSome(equalTo(seed)))
    },
    test("set seed on multiple wallets") {
      for {
        storage <- ZIO.service[WalletSecretStorage]
        wallets <- ZIO.foreach(1 to 10) { i =>
          for {
            seedDigest <- Random.nextBytes(32).map(_.toArray)
            wallet <- ZIO.serviceWithZIO[WalletNonSecretStorage](
              _.createWallet(Wallet(s"wallet-$i"), seedDigest)
            )
            seed = WalletSeed.fromByteArray(Array.fill[Byte](64)(i.toByte)).toOption.get
            walletAccessCtx = ZLayer.succeed(WalletAccessContext(wallet.id))
            _ <- storage.setWalletSeed(seed).provideSomeLayer(walletAccessCtx)
          } yield wallet
        }
        seeds <- ZIO
          .foreach(wallets) { wallet =>
            val walletAccessCtx = ZLayer.succeed(WalletAccessContext(wallet.id))
            storage.findWalletSeed.provideSomeLayer(walletAccessCtx)
          }
          .map(_.flatten)
      } yield assert(seeds.size)(equalTo(10)) && assert(seeds)(isDistinct)
    }
  )

}
