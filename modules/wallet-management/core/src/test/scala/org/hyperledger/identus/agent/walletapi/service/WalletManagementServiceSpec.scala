package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.{Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceError.{
  DuplicatedWalletSeed,
  TooManyPermittedWallet,
  TooManyWebhookError
}
import org.hyperledger.identus.agent.walletapi.sql.{JdbcWalletNonSecretStorage, JdbcWalletSecretStorage}
import org.hyperledger.identus.agent.walletapi.storage.WalletSecretStorage
import org.hyperledger.identus.agent.walletapi.vault.VaultWalletSecretStorage
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.shared.crypto.ApolloSpecHelper
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext, WalletId}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.{DBTestUtils, VaultTestContainerSupport}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.net.URI

object WalletManagementServiceSpec
    extends ZIOSpecDefault,
      PostgresTestContainerSupport,
      ApolloSpecHelper,
      VaultTestContainerSupport {

  override def spec = {
    def testSuite(name: String) =
      suite(name)(
        createWalletSpec.provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin())),
        getWalletSpec.provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin())),
        multitenantSpec,
        webhookSpec
      ) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB) @@ TestAspect.sequential

    val suite1 = testSuite("jdbc as secret storage")
      .provide(
        WalletManagementServiceImpl.layer,
        JdbcWalletNonSecretStorage.layer,
        JdbcWalletSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        apolloLayer,
      )

    val suite2 = testSuite("vault as secret storage")
      .provide(
        WalletManagementServiceImpl.layer,
        JdbcWalletNonSecretStorage.layer,
        VaultWalletSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        apolloLayer,
        vaultKvClientLayer(),
      )

    suite("WalletManagementService")(suite1, suite2)
  }

  private def getWalletSpec = suite("getWallet")(
    test("get existing wallet") {
      for {
        svc <- ZIO.service[WalletManagementService]
        createdWallet <- svc.createWallet(Wallet("wallet-1"))
        wallet <- svc.findWallet(createdWallet.id)
      } yield assert(wallet)(isSome(equalTo(createdWallet)))
    },
    test("get non-existing wallet") {
      for {
        svc <- ZIO.service[WalletManagementService]
        wallet <- svc.findWallet(WalletId.random)
      } yield assert(wallet)(isNone)
    },
  )

  private def createWalletSpec = suite("createWallet")(
    test("initialize with no wallet") {
      for {
        svc <- ZIO.service[WalletManagementService]
        walletIds <- svc.listWallets().map(_._1)
      } yield assert(walletIds)(isEmpty)
    },
    test("create a wallet with random seed") {
      for {
        svc <- ZIO.service[WalletManagementService]
        secretStorage <- ZIO.service[WalletSecretStorage]
        createdWallet <- svc.createWallet(Wallet("wallet-1"))
        listedWallets <- svc.listWallets().map(_._1)
        seed <- secretStorage.findWalletSeed.provide(ZLayer.succeed(WalletAccessContext(createdWallet.id)))
      } yield assert(listedWallets)(hasSameElements(Seq(createdWallet))) &&
        assert(seed)(isSome)
    },
    test("create multiple wallets with random seed") {
      for {
        svc <- ZIO.service[WalletManagementService]
        secretStorage <- ZIO.service[WalletSecretStorage]
        createdWallets <- ZIO.foreach(1 to 10)(i => svc.createWallet(Wallet(s"wallet-$i")))
        listedWallets <- svc.listWallets().map(_._1)
        seeds <- ZIO.foreach(listedWallets) { wallet =>
          secretStorage.findWalletSeed.provide(ZLayer.succeed(WalletAccessContext(wallet.id)))
        }
      } yield assert(createdWallets)(hasSameElements(listedWallets)) &&
        assert(seeds)(forall(isSome))
    },
    test("create a wallet with provided seed") {
      for {
        svc <- ZIO.service[WalletManagementService]
        secretStorage <- ZIO.service[WalletSecretStorage]
        seed1 = WalletSeed.fromByteArray(Array.fill[Byte](64)(0)).toOption.get
        createdWallet <- svc.createWallet(Wallet("wallet-1"), Some(seed1))
        listedWallets <- svc.listWallets().map(_._1)
        seed2 <- secretStorage.findWalletSeed.provide(ZLayer.succeed(WalletAccessContext(createdWallet.id)))
      } yield assert(listedWallets)(hasSameElements(Seq(createdWallet))) &&
        assert(seed2)(isSome(equalTo(seed1)))
    },
    test("create multiple wallets with provided seed") {
      for {
        svc <- ZIO.service[WalletManagementService]
        secretStorage <- ZIO.service[WalletSecretStorage]
        seeds1 = (1 to 10).map(i => WalletSeed.fromByteArray(Array.fill[Byte](64)(i.toByte)).toOption.get)
        createdWallets <- ZIO.foreach(seeds1) { seed => svc.createWallet(Wallet("test-wallet"), Some(seed)) }
        listedWallets <- svc.listWallets().map(_._1)
        seeds2 <- ZIO.foreach(listedWallets) { wallet =>
          secretStorage.findWalletSeed.provide(ZLayer.succeed(WalletAccessContext(wallet.id)))
        }
      } yield assert(createdWallets)(hasSameElements(listedWallets)) &&
        assert(seeds2.flatten)(hasSameElements(seeds1))
    },
    test("create multiple wallets with same seed must fail") {
      for {
        svc <- ZIO.service[WalletManagementService]
        seed = WalletSeed.fromByteArray(Array.fill[Byte](64)(0)).toOption.get
        _ <- svc.createWallet(Wallet("wallet-1"), Some(seed))
        exit <- svc.createWallet(Wallet("wallet-2"), Some(seed)).exit
      } yield assert(exit)(fails(isSubtype[DuplicatedWalletSeed](anything)))
    },
    test("cannot create new wallet for self-service if already have permitted wallet") {
      val walletId = WalletId.random
      for {
        svc <- ZIO.service[WalletManagementService]
        exit <- svc
          .createWallet(Wallet("wallet-1"))
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId))))
          .exit
      } yield assert(exit)(fails(isSubtype[TooManyPermittedWallet](anything)))
    }
  )

  private def multitenantSpec = suite("multitenant spec")(
    test("get all wallets for admin") {
      for {
        svc <- ZIO.service[WalletManagementService]
        wallet1 <- svc.createWallet(Wallet("wallet-1")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- svc.createWallet(Wallet("wallet-2")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet3 <- svc.createWallet(Wallet("wallet-3")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        walletIds = Seq(wallet1, wallet2, wallet3).map(_.id)
        wallets1 <- svc.getWallets(walletIds).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallets2 <- svc
          .listWallets()
          .map(_._1)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      } yield assert(wallets1.map(_.id))(equalTo(walletIds)) &&
        assert(wallets2.map(_.id))(equalTo(walletIds))
    },
    test("get only permitted wallet for self-service") {
      for {
        svc <- ZIO.service[WalletManagementService]
        wallet1 <- svc.createWallet(Wallet("wallet-1")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- svc.createWallet(Wallet("wallet-2")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet3 <- svc.createWallet(Wallet("wallet-3")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        walletIds = Seq(wallet1, wallet2, wallet3).map(_.id)
        permittedWalletIds = Seq(wallet1, wallet2).map(_.id)
        wallets1 <- svc
          .getWallets(walletIds)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(permittedWalletIds)))
        wallets2 <- svc
          .listWallets()
          .map(_._1)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(permittedWalletIds)))
      } yield assert(wallets1.map(_.id))(equalTo(permittedWalletIds)) &&
        assert(wallets2.map(_.id))(equalTo(permittedWalletIds))
    },
    test("cannot get wallet by self-service that is not permitted") {
      for {
        svc <- ZIO.service[WalletManagementService]
        wallet1 <- svc.createWallet(Wallet("wallet-1")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- svc.createWallet(Wallet("wallet-2")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet3 <- svc.createWallet(Wallet("wallet-3")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        walletIds = Seq(wallet1, wallet2, wallet3).map(_.id)
        permittedWalletIds = Seq(wallet1, wallet2).map(_.id)
        maybeWallet3 <- svc
          .findWallet(wallet3.id)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(permittedWalletIds)))
      } yield assert(maybeWallet3)(isNone)
    }
  )

  private def webhookSpec = suite("webhook spec")(
    test("cannot create more notifications than the limit") {
      for {
        svc <- ZIO.service[WalletManagementService]
        wallet1 <- svc.createWallet(Wallet("wallet-1")).provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        exit <- (for {
          _ <- ZIO.iterate(1)(_ <= WalletManagementServiceImpl.MAX_WEBHOOK_PER_WALLET)(s =>
            for {
              config <- EventNotificationConfig.applyWallet(URI.create("http://fake.host").toURL, Map.empty)
              _ <- svc.createWalletNotification(config)
            } yield s + 1
          )
          oneConfigTooMuch <- EventNotificationConfig.applyWallet(URI.create("http://fake.host").toURL, Map.empty)
          exit <- svc.createWalletNotification(oneConfigTooMuch).exit
        } yield exit).provide(ZLayer.succeed(WalletAccessContext(wallet1.id)))
      } yield assert(exit)(fails(isSubtype[TooManyWebhookError](anything)))
    }
  )

}
