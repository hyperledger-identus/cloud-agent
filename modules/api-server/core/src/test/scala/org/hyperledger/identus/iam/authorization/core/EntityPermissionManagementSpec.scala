package org.hyperledger.identus.iam.authorization.core

import org.hyperledger.identus.agent.walletapi.model.{Entity, Wallet}
import org.hyperledger.identus.agent.walletapi.service.{
  EntityService,
  EntityServiceImpl,
  WalletManagementService,
  WalletManagementServiceImpl
}
import org.hyperledger.identus.agent.walletapi.sql.{
  JdbcEntityRepository,
  JdbcWalletNonSecretStorage,
  JdbcWalletSecretStorage
}
import org.hyperledger.identus.iam.authorization.core.PermissionManagementServiceError.{
  ServiceError,
  WalletNotFoundById
}
import org.hyperledger.identus.shared.crypto.ApolloSpecHelper
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.DBTestUtils
import zio.*
import zio.test.*
import zio.test.Assertion.*

object EntityPermissionManagementSpec extends ZIOSpecDefault, PostgresTestContainerSupport, ApolloSpecHelper {

  override def spec = {
    val s = suite("EntityPermissionManagementSpec")(
      successfulCasesSuite,
      failureCasesSuite,
      multitenantSuite
    ) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

    s.provide(
      EntityPermissionManagementService.layer,
      EntityServiceImpl.layer,
      WalletManagementServiceImpl.layer,
      JdbcEntityRepository.layer,
      JdbcWalletNonSecretStorage.layer,
      JdbcWalletSecretStorage.layer,
      contextAwareTransactorLayer,
      systemTransactorLayer,
      pgContainerLayer,
      apolloLayer
    )
  }.provide(Runtime.removeDefaultLoggers)

  private val successfulCasesSuite = suite("Successful cases")(
    test("grant wallet access to the user") {
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagementService[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- walletService
          .createWallet(Wallet("test2"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService
          .create(Entity("alice", wallet1.id.toUUID))
        _ <- permissionService
          .grantWalletToUser(wallet2.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.getById(entity.id)
        permissions <- permissionService
          .listWalletPermissions(entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      } yield assert(permissions.head)(equalTo(wallet2.id))
    },
  )

  private val failureCasesSuite = suite("Failure Cases")(
    test("revoke wallet is not support") {
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagementService[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService
          .create(Entity("alice", wallet1.id.toUUID))
        exit <- permissionService
          .revokeWalletFromUser(wallet1.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
          .exit
      } yield assert(exit)(fails(isSubtype[ServiceError](anything)))
    }
  )

  private val multitenantSuite = suite("multi-tenant cases")(
    test("grant wallet access to the user by self-service") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagementService[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test", walletId1))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- walletService
          .createWallet(Wallet("test2", walletId2))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.create(Entity("alice", wallet1.id.toUUID))
        _ <- permissionService
          .grantWalletToUser(wallet2.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId2))))
        entity <- entityService.getById(entity.id)
        permissions <- permissionService
          .listWalletPermissions(entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      } yield assert(permissions.head)(equalTo(wallet2.id))
    },
    test("grant wallet access to non-permitted wallet by self-service is not allowed") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagementService[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test", walletId1))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.create(Entity("alice", wallet1.id.toUUID))
        exit <- permissionService
          .grantWalletToUser(walletId2, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId1))))
          .exit
      } yield assert(exit)(fails(isSubtype[WalletNotFoundById](anything)))
    },
  )

}
