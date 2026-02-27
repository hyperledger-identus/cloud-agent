package org.hyperledger.identus.pollux.sql

import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.*
import doobie.util.transactor.Transactor
import org.hyperledger.identus.pollux.core.model.{
  CredentialSchemaAndTrustedIssuersConstraint,
  VerificationPolicy,
  VerificationPolicyConstraint
}
import org.hyperledger.identus.pollux.core.repository.VerificationPolicyRepository
import org.hyperledger.identus.pollux.sql.model.db.VerificationPolicySql
import org.hyperledger.identus.pollux.sql.repository.JdbcVerificationPolicyRepository
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.MigrationAspects.*
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object VerificationPolicySqlIntegrationSpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val repositoryLayer =
    contextAwareTransactorLayer >>> JdbcVerificationPolicyRepository.layer
  private val testEnvironmentLayer =
    zio.test.testEnvironment ++
      pgContainerLayer ++
      contextAwareTransactorLayer ++
      repositoryLayer ++
      ZLayer.succeed(WalletAccessContext(WalletId.random))

  def spec = {
    val singleWalletSuite =
      ((verificationPolicyCRUDSuite + verificationPolicyLookupSuite) @@ nondeterministic @@ sequential @@ timed @@ migrate(
        schema = "public",
        paths = "classpath:sql/pollux"
      )).provideSomeLayerShared(testEnvironmentLayer)

    val multiWalletSuite = (multiWalletVerificationPolicyCRUDSuite @@ migrateEach(
      schema = "public",
      paths = "classpath:sql/pollux"
    )).provide(pgContainerLayer, contextAwareTransactorLayer, JdbcVerificationPolicyRepository.layer)

    suite("verification policy DAL spec")(singleWalletSuite, multiWalletSuite)
  }

  val multiWalletVerificationPolicyCRUDSuite = suite("verification policy multi-wallet CRUD operations")(
    test("do not see records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[VerificationPolicyRepository]
        record <- VerificationPolicyGen.verificationPolicyZIO
          .runCollectN(1)
          .flatMap(_.head)
        _ <- repo.create(record).provide(wallet1)
        ownRecord <- repo.findById(record.id).provide(wallet1)
        crossRecord <- repo.findById(record.id).provide(wallet2)
      } yield assert(ownRecord)(isSome(equalTo(record))) && assert(crossRecord)(isNone)
    },
    test("total count do not consider records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[VerificationPolicyRepository]
        record <- VerificationPolicyGen.verificationPolicyZIO
          .runCollectN(1)
          .flatMap(_.head)
        _ <- repo.create(record).provide(wallet1)
        n1 <- repo.totalCount().provide(wallet1)
        n2 <- repo.totalCount().provide(wallet2)
      } yield assert(n1)(equalTo(1)) && assert(n2)(isZero)
    },
    test("do not delete records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        repo <- ZIO.service[VerificationPolicyRepository]
        record1 <- VerificationPolicyGen.verificationPolicyZIO
          .runCollectN(1)
          .flatMap(_.head)
        _ <- repo.create(record1).provide(wallet1)
        deleteResult <- repo.delete(record1.id).provide(wallet2).exit
        actualRecord <- repo.findById(record1.id).provide(wallet1)
      } yield assert(deleteResult)(failsCause(anything)) &&
        assert(actualRecord)(isSome(equalTo(record1)))
    }
  )

  val verificationPolicyCRUDSuite =
    suite("verification policy CRUD operations")(
      test("insert, findById, update and delete operations") {
        for {
          tx <- ZIO.service[Transactor[ContextAwareTask]]
          repo <- ZIO.service[VerificationPolicyRepository]

          expectedCreated <- VerificationPolicyGen.verificationPolicyZIO
            .runCollectN(1)
            .flatMap(_.head)
          actualCreated <- repo.create(expectedCreated)
          getByIdCreated <- repo.findById(expectedCreated.id)

          allRecordsAreSimilar = assert(expectedCreated)(
            equalTo(actualCreated)
          ) &&
            assert(getByIdCreated)(isSome(equalTo(actualCreated)))

          isCreated = assert(actualCreated)(equalTo(expectedCreated)) &&
            assert(getByIdCreated)(isSome(equalTo(expectedCreated)))

          expectedUpdated = expectedCreated.copy(
            name = "new name",
            description = "new description"
          )
          actualUpdated <- repo
            .update(actualCreated.id, actualCreated.nonce, expectedUpdated)
          getByIdUpdated <- repo.findById(expectedUpdated.id).map(_.get)

          isUpdated = assert(actualUpdated)(equalTo(expectedUpdated.copy(updatedAt = actualUpdated.updatedAt))) &&
            assert(getByIdUpdated)(equalTo(expectedUpdated.copy(updatedAt = actualUpdated.updatedAt)))

          //

          expectedUpdated2 = actualUpdated.copy(
            name = "new name 2 ",
            description = "new description 2"
          )
          actualUpdated2 <- repo
            .update(actualUpdated.id, actualUpdated.nonce, expectedUpdated2)
          getByIdUpdated2 <- repo.findById(expectedUpdated2.id).map(_.get)

          isUpdated2 = assert(actualUpdated2)(equalTo(expectedUpdated2.copy(updatedAt = actualUpdated.updatedAt))) &&
            assert(getByIdUpdated2)(equalTo(expectedUpdated2.copy(updatedAt = actualUpdated.updatedAt)))

          //

          actualDeleted <- repo.delete(expectedUpdated.id)

          isDeletedReturnedBack = assert(actualDeleted)(
            equalTo(actualUpdated2)
          )
          getByIdDeleted <- repo.findById(actualUpdated.id)

          isDeleted = assert(getByIdDeleted)(isNone)

          _ <- VerificationPolicySql.deleteAll().transactWallet(tx)
        } yield isCreated && allRecordsAreSimilar && isUpdated && isDeletedReturnedBack && isDeleted
      },
      deleteAllVerificationPoliciesTest,
      insertNVerificationPoliciesTest(100),
      deleteAllVerificationPoliciesTest
    ) @@ nondeterministic @@ sequential @@ timed

  def insertNVerificationPoliciesTest(n: Int) =
    test(s"insert $n verification policies entries") {
      for {
        repo <- ZIO.service[VerificationPolicyRepository]

        generatedVerificationPolicies: List[VerificationPolicy] <-
          VerificationPolicyGen.verificationPolicyZIO
            .runCollectN(n)
            .flatMap(ZIO.collectAll)

        _ <- ZIO.collectAll(
          generatedVerificationPolicies.map(vp => repo.create(vp))
        )

        totalCount <- repo.totalCount()

        allNEntitiesAreStored = assert(totalCount)(equalTo(n))
      } yield allNEntitiesAreStored
    }
  def deleteAllVerificationPoliciesTest =
    test("delete all verification policies entries") {
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]
        repo <- ZIO.service[VerificationPolicyRepository]

        _ <- VerificationPolicySql.deleteAll().transactWallet(tx)
        totalCount <- repo.totalCount()

        allEntitiesAreDeleted = assert(totalCount)(equalTo(0))
      } yield allEntitiesAreDeleted
    }

  val N = 30
  val verificationPolicyLookupSuite = suite("lookup operations")(
    insertNVerificationPoliciesTest(N),
    test("get all in one page") {
      for {
        repo <- ZIO.service[VerificationPolicyRepository]
        all <- repo.lookup(None, None, None)
        allNRecordsAreReturned = assert(all.length)(equalTo(N))
      } yield allNRecordsAreReturned
    },
    test("get all by two pages") {
      for {
        repo <- ZIO.service[VerificationPolicyRepository]
        first <- repo.lookup(None, offsetOpt = Some(0), limitOpt = Some(N / 2))
        second <- repo.lookup(None, offsetOpt = Some(N / 2), limitOpt = Some(N / 2))
        firstPageContainsAHalfOfTheRecords = assert(first.length)(
          equalTo(N / 2)
        )
        secondPageContainsAHalfOfTheRecords = assert(second.length)(
          equalTo(N - N / 2)
        )
        allNRecordsAreReturned = assert(first.length + second.length)(
          equalTo(N)
        )
      } yield firstPageContainsAHalfOfTheRecords &&
        secondPageContainsAHalfOfTheRecords &&
        allNRecordsAreReturned
    },
    test("paginate through the collection of verifiable policies") {
      for {
        repo <- ZIO.service[VerificationPolicyRepository]

        paginator = new Paginator(skipLimit =>
          repo.lookup(nameOpt = None, offsetOpt = Some(skipLimit.skip), limitOpt = Some(skipLimit.limit))
        )

        allItems1 <- paginator.fetchAll(SkipLimit(0, 1))
        allItems10 <- paginator.fetchAll(SkipLimit(0, 10))
        allItems20 <- paginator.fetchAll(SkipLimit(0, 20))
        totalCount <- repo.totalCount()

        allItemsArePaginated1 = assert(totalCount)(equalTo(allItems1.length))
        allItemsArePaginated10 = assert(totalCount)(equalTo(allItems10.length))
        allItemsArePaginated20 = assert(totalCount)(equalTo(allItems20.length))
      } yield allItemsArePaginated1 && allItemsArePaginated10 && allItemsArePaginated20
    }
  )

  object VerificationPolicyGen {
    val id = Gen.uuid
    val name =
      Gen.alphaNumericStringBounded(5, 10).map("Generated Policy Name " + _)
    val description = Gen.alphaNumericStringBounded(5, 30)

    val schemaId =
      Gen.alphaNumericStringBounded(30, 30).map("http://atala.io/schemas/" + _)
    val trustedIssuer =
      Gen.alphaNumericStringBounded(64, 64).map("did:prism:" + _)
    val trustedIssuers = Gen.setOfBounded(1, 10)(trustedIssuer).map(_.toVector)

    val verificationPolicyConstraint: Gen[Any, VerificationPolicyConstraint] =
      for {
        schemaId <- schemaId
        trustedIssuers <- trustedIssuers
      } yield CredentialSchemaAndTrustedIssuersConstraint(
        schemaId,
        trustedIssuers
      )

    val verificationPolicyZIO: Gen[Any, UIO[VerificationPolicy]] = for {
      name <- name
      description <- description
      constraints <- Gen
        .setOfBounded(min = 2, max = 10)(verificationPolicyConstraint)
        .map(_.toVector)
    } yield VerificationPolicy.make(
      name = name,
      description = description,
      constraints = constraints
    )
  }

  case class SkipLimit(skip: Int, limit: Int) {
    def next: SkipLimit = SkipLimit(skip + limit, limit)
  }
  class Paginator[R, T](page: SkipLimit => RIO[R, List[T]]) {
    def fetchAll(
        from: SkipLimit,
        acc: List[T] = List.empty[T]
    ): RIO[R, List[T]] = {
      val nextPage = page(from)
      nextPage.flatMap(items => items.headOption.fold(ZIO.succeed(acc))(nonEmpty => fetchAll(from.next, acc ++ items)))
    }

    def fetchPage(from: SkipLimit): RIO[R, List[T]] = page(from)
  }
}
