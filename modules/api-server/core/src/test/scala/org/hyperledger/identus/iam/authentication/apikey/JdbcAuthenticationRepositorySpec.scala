package org.hyperledger.identus.iam.authentication.apikey

import org.hyperledger.identus.container.util.MigrationAspects.migrate
import org.hyperledger.identus.iam.authentication.apikey.AuthenticationMethodType.ApiKey
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.Runtime.removeDefaultLoggers
import zio.ZIO

object JdbcAuthenticationRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  val entityIdGen = Gen.uuid
  val secretGen = Gen.alphaNumericStringBounded(256, 256)
  val entityIdAndSecretGen = entityIdGen <*> secretGen

  override def spec = {
    val testSuite =
      suite("JdbcAuthenticationRepositorySpec")(
        crudSpec
      ) @@ TestAspect.sequential @@ migrate(
        schema = "public",
        paths = "classpath:sql/agent"
      )

    testSuite
      .provideSomeLayerShared(
        pgContainerLayer >+> systemTransactorLayer >+> JdbcAuthenticationRepository.layer
      )
      .provide(removeDefaultLoggers)
  }

  private val crudSpec = suite("CRUD operations on the AuthenticationRepository")(
    test("create, read, update, delete") {
      check(entityIdAndSecretGen) { case (entityId, secret) =>
        for {
          repository <- ZIO.service[AuthenticationRepository]
          recordId <- repository.insert(entityId, AuthenticationMethodType.ApiKey, secret)
          fetchedEntityId <- repository.findEntityIdByMethodAndSecret(AuthenticationMethodType.ApiKey, secret)
          _ <- repository.deleteByMethodAndEntityId(entityId, AuthenticationMethodType.ApiKey)
          notFoundEntityId <- repository.findEntityIdByMethodAndSecret(AuthenticationMethodType.ApiKey, secret)
        } yield assert(fetchedEntityId)(isSome(equalTo(entityId))) &&
          assert(notFoundEntityId)(isNone)
      }
    },
    test("insert a similar secret for a different tenant must fail") {
      check(entityIdAndSecretGen <*> entityIdGen) { case (entityId, secret, anotherEntityId) =>
        for {
          repository <- ZIO.service[AuthenticationRepository]
          _ <- repository.insert(entityId, AuthenticationMethodType.ApiKey, secret)
          insertSameSecret <- repository.insert(anotherEntityId, AuthenticationMethodType.ApiKey, secret).flip
          authenticationMethod <- repository
            .findAuthenticationMethodByTypeAndSecret(AuthenticationMethodType.ApiKey, secret)
        } yield assert(insertSameSecret)(
          isSubtype[AuthenticationRepositoryError.AuthenticationCompromised](anything)
        ) &&
          assert(authenticationMethod)(isSome(anything)) &&
          assert(authenticationMethod.flatMap(_.deletedAt))(isSome(anything))
      }
    },
    test("insert a similar secret for the same tenant must succeed") {
      check(entityIdAndSecretGen) { case (entityId, secret) =>
        for {
          repository <- ZIO.service[AuthenticationRepository]
          _ <- repository.insert(entityId, AuthenticationMethodType.ApiKey, secret)
          _ <- repository.insert(entityId, AuthenticationMethodType.ApiKey, secret)
          authenticationMethod <- repository
            .findAuthenticationMethodByTypeAndSecret(AuthenticationMethodType.ApiKey, secret)
        } yield assert(authenticationMethod)(isSome(anything)) &&
          assert(authenticationMethod.flatMap(_.deletedAt))(isNone)
      }
    },
    test("insert a similar secret after deletion for the same tenant must fail") {
      check(entityIdAndSecretGen) { case (entityId, secret) =>
        for {
          repository <- ZIO.service[AuthenticationRepository]
          _ <- repository.insert(entityId, ApiKey, secret)
          _ <- repository.delete(entityId, ApiKey, secret)
          insertSameSecret <- repository.insert(entityId, ApiKey, secret).flip
          authenticationMethod <- repository
            .findAuthenticationMethodByTypeAndSecret(ApiKey, secret)
        } yield assert(insertSameSecret)(
          isSubtype[AuthenticationRepositoryError.AuthenticationCompromised](anything)
        ) &&
          assert(authenticationMethod)(isSome(anything)) &&
          assert(authenticationMethod.flatMap(_.deletedAt))(isSome(anything))
      }
    }
  ) @@ samples(10) @@ nondeterministic
}
