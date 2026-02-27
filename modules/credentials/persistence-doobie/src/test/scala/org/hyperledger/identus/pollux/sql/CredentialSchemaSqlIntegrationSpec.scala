package org.hyperledger.identus.pollux.sql

import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.*
import doobie.util.transactor.Transactor
import io.getquill.*
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.sql.model.db.{CredentialSchema, CredentialSchemaSql}
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.MigrationAspects.*
import zio.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.collection.mutable
import scala.io.Source

object CredentialSchemaSqlIntegrationSpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val testEnvironmentLayer =
    zio.test.testEnvironment ++
      pgContainerLayer ++
      contextAwareTransactorLayer ++
      ZLayer.succeed(WalletAccessContext(WalletId.random))

  object Vocabulary {
    val verifiableCredentialTypes =
      Source
        .fromResource("data/verifiableCredentialTypes.csv")(scala.io.Codec.UTF8)
        .getLines()
        .toSet
    val verifiableCredentialClaims = Source
      .fromResource("data/verifiableCredentialClaims.csv")(scala.io.Codec.UTF8)
      .getLines()
      .toSet
  }

  object Generators {
    val schemaId = Gen.uuid
    val schemaName =
      Gen.oneOf(Gen.fromIterable(Vocabulary.verifiableCredentialTypes))

    val schemaVersion = (Gen.int(1, 3) <*> Gen.int(0, 9) <*> Gen.int(0, 100))
      .map { case (major, minor, patch) => s"$major.$minor.$patch" }

    val schemaDescription = Gen.alphaNumericStringBounded(5, 30)

    val schemaAttribute =
      Gen.fromIterable(Vocabulary.verifiableCredentialClaims)
    val schemaAttributes = Gen.setOfBounded(1, 4)(schemaAttribute).map(_.toList)
    val jsonSchema =
      schemaAttributes.map(attributes => Json.Arr(attributes.map(Json.Str(_))*))
    val schemaAuthor =
      Gen.int(1000000, 9999999).map(i => s"did:prism:4fb06243213500578f59588de3e1dd9b266ec1b61e43b0ff86ad0712f$i")
    val schemaAuthored = Gen.offsetDateTime

    val schemaTag: Gen[Any, String] = Gen.alphaNumericStringBounded(3, 5)
    val schemaTags: Gen[Any, List[String]] =
      Gen.setOfBounded(0, 3)(schemaTag).map(_.toList)

    val schema: Gen[WalletAccessContext, CredentialSchema] = for {
      name <- schemaName
      version <- schemaVersion
      description <- schemaDescription
      attributes <- schemaAttributes
      schema <- jsonSchema
      tags <- schemaTags
      author <- schemaAuthor
      authored = OffsetDateTime.now(ZoneOffset.UTC)
      id = UUID.randomUUID()
      walletId <- Gen.fromZIO(ZIO.serviceWith[WalletAccessContext](_.walletId))
      resolutionMethod <- Gen.fromIterable(ResourceResolutionMethod.values)
    } yield CredentialSchema(
      guid = id,
      id = id,
      name = name,
      version = version,
      description = description,
      schema = JsonValue(schema),
      `type` = "AnonCreds",
      author = author,
      authored = authored,
      tags = tags,
      walletId = walletId,
      resolutionMethod = resolutionMethod
    ).withTruncatedTimestamp()

    private val unique = mutable.Set.empty[String]
    val schemaUnique = for {
      _ <-
        schema // drain the value to evade the Gen from producing the same over and over again
      s <- schema if !unique.contains(s.uniqueConstraintKey)
      _ = unique += s.uniqueConstraintKey
    } yield s
  }

  def spec = {
    val singleWalletSuite = (schemaRegistryCRUDSuite @@ nondeterministic @@ sequential @@ timed @@ migrate(
      schema = "public",
      paths = "classpath:sql/pollux"
    )).provideSomeLayerShared(testEnvironmentLayer)

    val multiWalletSuite = (multiWalletSchemaRegistryCRUDSuite @@ migrateEach(
      schema = "public",
      paths = "classpath:sql/pollux"
    )).provide(pgContainerLayer, contextAwareTransactorLayer)

    suite("schema-registry DAL spec")(singleWalletSuite, multiWalletSuite)
  }

  val multiWalletSchemaRegistryCRUDSuite = suite("schema-registry multi-wallet CRUD operations")(
    test("do not see records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]
        record <- Generators.schema.runCollectN(1).map(_.head).provide(wallet1)
        _ <- CredentialSchemaSql.insert(record).transactWallet(tx).provide(wallet1)
        ownRecord <- CredentialSchemaSql
          .findByGUID(record.guid, record.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)
          .provide(wallet1)
        crossRecord <- CredentialSchemaSql
          .findByGUID(record.guid, record.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)
          .provide(wallet2)
      } yield assert(ownRecord)(isSome(equalTo(record))) && assert(crossRecord)(isNone)
    },
    test("total count do not consider records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]
        record <- Generators.schema.runCollectN(1).map(_.head).provide(wallet1)
        _ <- CredentialSchemaSql.insert(record).transactWallet(tx).provide(wallet1)
        n1 <- CredentialSchemaSql.totalCount.transactWallet(tx).provide(wallet1)
        n2 <- CredentialSchemaSql.totalCount.transactWallet(tx).provide(wallet2)
      } yield assert(n1)(equalTo(1)) && assert(n2)(isZero)
    },
    test("do not delete records outside of the wallet") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
      val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]
        record1 <- Generators.schema.runCollectN(1).map(_.head).provide(wallet1)
        record2 <- Generators.schema.runCollectN(1).map(_.head).provide(wallet2)
        _ <- CredentialSchemaSql.insert(record1).transactWallet(tx).provide(wallet1)
        _ <- CredentialSchemaSql.insert(record2).transactWallet(tx).provide(wallet2)
        _ <- CredentialSchemaSql.deleteAll.transactWallet(tx).provide(wallet2)
        n1 <- CredentialSchemaSql.totalCount.transactWallet(tx).provide(wallet1)
        n2 <- CredentialSchemaSql.totalCount.transactWallet(tx).provide(wallet2)
      } yield assert(n1)(equalTo(1)) && assert(n2)(isZero)
    }
  )

  val schemaRegistryCRUDSuite = suite("schema-registry CRUD operations")(
    test("insert, findById, update and delete operations") {
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]

        expected <- Generators.schema.runCollectN(1).map(_.head)
        _ <- CredentialSchemaSql.insert(expected).transactWallet(tx)
        actual <- CredentialSchemaSql
          .findByGUID(expected.guid, expected.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)

        schemaCreated = assert(actual.get)(equalTo(expected))

        updatedExpected = expected.copy(name = "new name")
        updatedActual <- CredentialSchemaSql
          .update(updatedExpected)
          .transactWallet(tx)
        updatedActual2 <- CredentialSchemaSql
          .findByGUID(expected.id, expected.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)

        schemaUpdated =
          assert(updatedActual)(equalTo(updatedExpected)) &&
            assert(updatedActual2.get)(equalTo(updatedExpected))

        deleted <- CredentialSchemaSql.delete(expected.guid).transactWallet(tx)
        notFound <- CredentialSchemaSql
          .findByGUID(expected.guid, expected.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)

        schemaDeleted =
          assert(deleted)(equalTo(updatedExpected))
          assert(notFound)(isNone)

      } yield schemaCreated && schemaUpdated && schemaDeleted
    },
    test("insert N generated, findById, ensure constraint is not broken ") {
      for {
        tx <- ZIO.service[Transactor[ContextAwareTask]]
        _ <- CredentialSchemaSql.deleteAll.transactWallet(tx)

        generatedSchemas <- Generators.schemaUnique.runCollectN(10)

        allSchemasHaveUniqueId = assert(
          generatedSchemas
            .map(_.id)
            .toSet
            .count(_ => true)
        )(equalTo(generatedSchemas.length))

        allSchemasHaveUniqueConstraint = assert(
          generatedSchemas
            .map(_.uniqueConstraintKey)
            .toSet
            .count(_ => true)
        )(equalTo(generatedSchemas.length))

        _ <- ZIO.collectAll(
          generatedSchemas.map(schema => CredentialSchemaSql.insert(schema).transactWallet(tx))
        )

        firstActual = generatedSchemas.head
        firstExpected <- CredentialSchemaSql
          .findByGUID(firstActual.guid, firstActual.resolutionMethod)
          .transactWallet(tx)
          .map(_.headOption)

        schemaCreated = assert(firstActual)(equalTo(firstExpected.get))

        totalCount <- CredentialSchemaSql.totalCount.transactWallet(tx)
        lookupCountHttpSchemas <- CredentialSchemaSql
          .lookupCount(resolutionMethod = ResourceResolutionMethod.http)
          .transactWallet(tx)
        lookupCountDidSchemas <- CredentialSchemaSql
          .lookupCount(resolutionMethod = ResourceResolutionMethod.did)
          .transactWallet(tx)

        totalCountIsN = assert(totalCount)(equalTo(generatedSchemas.length))
        lookupCountIsN = assert(lookupCountHttpSchemas + lookupCountDidSchemas)(equalTo(generatedSchemas.length))

      } yield allSchemasHaveUniqueId &&
        allSchemasHaveUniqueConstraint &&
        schemaCreated &&
        totalCountIsN && lookupCountIsN
    }
  ) @@ nondeterministic @@ sequential @@ timed
}
