package org.hyperledger.identus.sharedtest.containers

import zio.test.*
import zio.test.TestAspect.*
import zio.ZIO

import scala.jdk.CollectionConverters.*
import scala.util.Try
object KeycloakTestContainerSupportSpec extends ZIOSpecDefault with KeycloakTestContainerSupport {

  override def spec = suite("KeycloakTestContainerSupportSpec")(
    test("Keycloak container should be started") {
      for {
        keycloakContainer <- ZIO.service[KeycloakContainerCustom]
      } yield assertTrue(keycloakContainer.container.isRunning)
    },
    test("Keycloak admin-client is initialized") {
      for {
        adminClient <- adminClientZIO
        usersCount <- ZIO.fromTry(Try(adminClient.realm("master").users().count()))
      } yield assertCompletes
    },
    test("`atala-test` realm is created") {
      for {
        adminClient <- adminClientZIO
        _ = adminClient.realms().create(realmRepresentation)
        realmCreated = adminClient.realms().findAll().asScala.exists(_.getRealm == realmRepresentation.getRealm)
      } yield assertTrue(realmCreated)
    },
    test("The Agent client is created") {
      for {
        adminClient <- adminClientZIO
        _ = adminClient.realm(realmRepresentation.getRealm).clients().create(agentClientRepresentation)
        clientCreated = adminClient
          .realm(realmRepresentation.getRealm)
          .clients()
          .findAll()
          .asScala
          .exists(_.getClientId == agentClientRepresentation.getClientId)
      } yield assertTrue(clientCreated)
    }
  ).provideLayerShared(keycloakContainerLayer >+> keycloakAdminClientLayer) @@ sequential
}
