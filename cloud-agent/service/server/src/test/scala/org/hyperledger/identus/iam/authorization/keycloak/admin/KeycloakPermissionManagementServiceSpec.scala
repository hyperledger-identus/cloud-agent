package org.hyperledger.identus.iam.authorization.keycloak.admin

import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.agent.walletapi.service.{WalletManagementService, WalletManagementServiceImpl}
import org.hyperledger.identus.agent.walletapi.sql.{JdbcWalletNonSecretStorage, JdbcWalletSecretStorage}
import org.hyperledger.identus.iam.authentication.oidc.*
import org.hyperledger.identus.iam.authentication.AuthenticationError.ResourceNotPermitted
import org.hyperledger.identus.iam.authorization.core.PermissionManagementService
import org.hyperledger.identus.iam.authorization.core.PermissionManagementServiceError.WalletNotFoundById
import org.hyperledger.identus.shared.crypto.ApolloSpecHelper
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletAdministrationContext, WalletId}
import org.hyperledger.identus.sharedtest.containers.{
  KeycloakContainerCustom,
  KeycloakTestContainerSupport,
  PostgresTestContainerSupport
}
import org.hyperledger.identus.test.container.DBTestUtils
import zio.*
import zio.http.Client
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import java.util.UUID

/*  testOnly org.hyperledger.identus.iam.authorization.keycloak.admin.KeycloakPermissionManagementServiceSpec */
object KeycloakPermissionManagementServiceSpec
    extends ZIOSpecDefault
    with KeycloakTestContainerSupport
    with KeycloakConfigUtils
    with PostgresTestContainerSupport
    with ApolloSpecHelper {

  override def spec = {
    val s = suite("KeycloakPermissionManagementServiceSpec")(
      successfulCasesSuite,
      failureCasesSuite,
      multitenantSuite
    ) @@ sequential @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

    s
      .provideSome[KeycloakContainerCustom](
        Client.default,
        keycloakAdminConfigLayer,
        KeycloakAdmin.layer,
        KeycloakPermissionManagementService.layer,
        KeycloakAuthenticatorImpl.layer,
        ZLayer.fromZIO(initializeClient) >>> KeycloakClientImpl.authzClientLayer >+> KeycloakClientImpl.layer,
        keycloakConfigLayer(),
        WalletManagementServiceImpl.layer,
        JdbcWalletNonSecretStorage.layer,
        JdbcWalletSecretStorage.layer,
        contextAwareTransactorLayer,
        pgContainerLayer,
        apolloLayer
      )
      .provideLayerShared(keycloakContainerLayer)
      .provide(Runtime.removeDefaultLoggers)
  }

  private val successfulCasesSuite = suite("Successful Cases")(
    test("grant wallet access to the user") {
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService.createWallet(Wallet("test_1"))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        _ <- permissionService.grantWalletToUser(wallet.id, entity)

        token <- client.getAccessToken(username, password).map(_.access_token)

        entity <- authenticator.authenticate(token)
        permittedWallet <- authenticator.authorizeWalletAccess(entity)
      } yield assert(wallet.id)(equalTo(permittedWallet.walletId))
    },
    test("revoke the wallet access from the user") {
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService.createWallet(Wallet("test_2"))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        _ <- permissionService.grantWalletToUser(wallet.id, entity)

        token <- client.getAccessToken(username, password).map(_.access_token)

        entity <- authenticator.authenticate(token)
        permittedWallet <- authenticator.authorizeWalletAccess(entity)

        _ <- permissionService.revokeWalletFromUser(wallet.id, entity)

        token2 <- client.getAccessToken(username, password).map(_.access_token)
        entity2 <- authenticator.authenticate(token)
        permittedWallet2 <- authenticator.authorizeWalletAccess(entity).exit

      } yield assert(permittedWallet2)(fails(isSubtype[ResourceNotPermitted](anything)))
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

  private val failureCasesSuite = suite("Failure Cases Suite")(
    test("grant wallet access to the user with invalid wallet id") {
      for {
        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        entity = KeycloakEntity(id = UUID.randomUUID())
        exit <- permissionService.grantWalletToUser(WalletId.random, entity).exit
      } yield assert(exit)(fails(isSubtype[WalletNotFoundById](anything)))
    },
    test("grant wallet access to the user with invalid user id") {
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]
        wallet <- walletService.createWallet(Wallet("test_1"))
        entity = KeycloakEntity(id = UUID.randomUUID())
        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        exit <- permissionService.grantWalletToUser(wallet.id, entity).exit
      } yield assert(exit)(dies(hasMessage(equalTo(s"Error creating policy for resource [${wallet.id}]"))))
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

  private val multitenantSuite = suite("multi-tenant cases")(
    test("grant wallet access to the user by self-service") {
      val walletId = WalletId.random
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService
          .createWallet(Wallet("test_1", walletId))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        _ <- permissionService
          .grantWalletToUser(wallet.id, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId))))

        token <- client.getAccessToken(username, password).map(_.access_token)

        entity <- authenticator.authenticate(token)
        permittedWallet <- authenticator.authorizeWalletAccess(entity)
      } yield assert(wallet.id)(equalTo(permittedWallet.walletId))
    },
    test("revoke wallet access from the user by self-service") {
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService
          .createWallet(Wallet("test_2"))
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        _ <- permissionService
          .grantWalletToUser(wallet.id, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

        token <- client.getAccessToken(username, password).map(_.access_token)

        entity <- authenticator.authenticate(token)
        permittedWallet <- authenticator.authorizeWalletAccess(entity)

        _ <- permissionService
          .revokeWalletFromUser(wallet.id, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(wallet.id))))

        token2 <- client.getAccessToken(username, password).map(_.access_token)
        entity2 <- authenticator.authenticate(token)
        permittedWallet2 <- authenticator.authorizeWalletAccess(entity).exit

      } yield assert(permittedWallet2)(fails(isSubtype[ResourceNotPermitted](anything)))
    },
    test("grant wallet access to non-permitted wallet by self-service is not allowed") {
      val walletId = WalletId.random
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService
          .createWallet(Wallet("test_1", walletId))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        exit <- permissionService
          .grantWalletToUser(WalletId.random, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId))))
          .exit
      } yield assert(exit)(fails(isSubtype[WalletNotFoundById](anything)))
    },
    test("revoke wallet access from non-permitted wallet by self-service is not allowed") {
      for {
        client <- ZIO.service[KeycloakClient]
        authenticator <- ZIO.service[KeycloakAuthenticator]
        walletService <- ZIO.service[WalletManagementService]

        wallet <- walletService
          .createWallet(Wallet("test_2"))
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

        randomId = UUID.randomUUID().toString
        username = "user_" + randomId
        password = randomId
        user <- createUser(username = username, password = password)
        entity = KeycloakEntity(id = UUID.fromString(user.getId))

        permissionService <- ZIO.service[PermissionManagementService[KeycloakEntity]]
        _ <- permissionService
          .grantWalletToUser(wallet.id, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))

        token <- client.getAccessToken(username, password).map(_.access_token)

        entity <- authenticator.authenticate(token)
        permittedWallet <- authenticator.authorizeWalletAccess(entity)

        exit <- permissionService
          .revokeWalletFromUser(wallet.id, entity)
          .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(WalletId.random))))
          .exit
      } yield assert(exit)(fails(isSubtype[WalletNotFoundById](anything)))
    }
  )
}
