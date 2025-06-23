package org.hyperledger.identus.iam.authorization.keycloak.admin

import org.hyperledger.identus.sharedtest.containers.{KeycloakContainerCustom, KeycloakTestContainerSupport}
import zio.*
import zio.test.*
import zio.test.Assertion.equalTo
import zio.test.TestAspect.*

import scala.util.Try

object KeycloakAdminSpec extends ZIOSpecDefault with KeycloakTestContainerSupport with KeycloakConfigUtils {

  override def spec = suite("KeycloakAdminSpec")(
    test("KeycloakAdmin can be created from the container") {
      for {
        keycloakContainer <- ZIO.service[KeycloakContainerCustom]
        config <- ZIO.service[KeycloakAdminConfig]

        keycloakAdmin <- KeycloakAdmin(config)
        usersCount <- ZIO.fromTry(
          Try(keycloakAdmin.realm("master").users().count()).map(_.toInt)
        )
      } yield assertTrue(keycloakContainer.container.isRunning) && assert(usersCount)(equalTo(1))
    }
  ).provideLayerShared(keycloakContainerLayer >+> keycloakAdminConfigLayer) @@ sequential

}
