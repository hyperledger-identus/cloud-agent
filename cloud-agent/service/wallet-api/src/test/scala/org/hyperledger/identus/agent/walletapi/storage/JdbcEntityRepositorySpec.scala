package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.{Entity, Wallet}
import org.hyperledger.identus.agent.walletapi.sql.{EntityRepository, JdbcEntityRepository, JdbcWalletNonSecretStorage}
import org.hyperledger.identus.shared.models.WalletId
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.DBTestUtils
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.util.UUID

object JdbcEntityRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  // TODO: move messages to the domain errors
  private def createRandomEntity = for {
    clock <- ZIO.clock
    id = UUID.randomUUID()
    walletId = UUID.randomUUID()
    createdAt <- clock.instant
    updatedAt <- clock.instant
    entity <- ZIO.succeed(
      Entity(id = id, name = "test", walletId = walletId, createdAt = createdAt, updatedAt = updatedAt)
    )
  } yield entity

  private def createAndStoreWallet(entity: Entity) = {
    for {
      storage <- ZIO.service[WalletNonSecretStorage]
      seedDigest <- Random.nextBytes(32).map(_.toArray)
      wallet <- storage.createWallet(Wallet("test", WalletId.fromUUID(entity.walletId)), seedDigest)
    } yield wallet
  }

  override def spec = {
    val testSuite =
      suite("JdbcEntityRepositorySpec")(
        createEntitySpec,
        getEntitySpec,
        updateEntitySpec,
        getAllEntitiesSpec
      ) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB) @@ TestAspect.sequential

    testSuite
      .provide(
        pgContainerLayer,
        systemTransactorLayer,
        contextAwareTransactorLayer,
        JdbcEntityRepository.layer,
        JdbcWalletNonSecretStorage.layer
      )
      .provide(Runtime.removeDefaultLoggers)
  }

  private val getAllEntitiesSpec = suite("get all entities spec")(
    test("get all entities - single entity") {
      for {
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        _ <- EntityRepository.insert(in)
        entities <- EntityRepository.getAll(0, 100)
        _ <- EntityRepository.delete(in.id)
      } yield assert(entities)(hasSize(equalTo(1))) &&
        assert(entities.head)(equalTo(in))
    },
    test("get all entities - 100 entities") {
      for {
        entities <- ZIO.foreach(1 to 100) { _ =>
          for {
            e <- createRandomEntity
            _ <- createAndStoreWallet(e)
            _ <- EntityRepository.insert(e)
          } yield e
        }
        allEntities <- EntityRepository.getAll(0, 100)
      } yield assert(allEntities)(hasSize(equalTo(100)))
    }
  )

  private val updateEntitySpec = suite("update the Entity spec")(
    test("update the Entity name") {
      for {
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
        _ <- EntityRepository.updateName(in.id, "newName")
        updated <- EntityRepository.getById(in.id)
      } yield assert(out)(equalTo(in)) &&
        assert(updated.name)(equalTo("newName"))
    },
    test("update the Entity name by id that does not exist") {
      for {
        in <- createRandomEntity
        updated <- EntityRepository.updateName(in.id, "newName").exit
      } yield assert(updated)(dies(anything))
    },
    test("update the Entity walletId") {
      for {
        random <- ZIO.random
        _ <- random.setSeed(52L)
        walletId <- random.nextUUID
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        storage <- ZIO.service[WalletNonSecretStorage]
        _ <- storage.createWallet(Wallet("another wallet", WalletId.fromUUID(walletId)), Array.emptyByteArray)
        out <- EntityRepository.insert(in)
        _ <- EntityRepository.updateWallet(in.id, walletId)
        updated <- EntityRepository.getById(in.id)
      } yield assert(out)(equalTo(in)) &&
        assert(updated.walletId)(equalTo(walletId))
    },
    test("update the Entity walletId by id that does not exist") {
      for {
        random <- ZIO.random
        _ <- random.setSeed(62L)
        id <- random.nextUUID
        walletId <- random.nextUUID
        updated <- EntityRepository.updateWallet(id, walletId).exit
      } yield assert(updated)(dies(anything))
    },
    test("update the Entity walletId by the walletId that does not exist") {
      for {
        random <- ZIO.random
        _ <- random.setSeed(56L)
        randomWalletId <- random.nextUUID

        in <- createRandomEntity
        wallet <- createAndStoreWallet(in)
        entity <- EntityRepository.insert(in)

        updated <- EntityRepository.updateWallet(entity.id, randomWalletId).exit
      } yield assert(updated)(dies(anything))
    },
  )

  private val getEntitySpec = suite("get the Entity spec")(
    test("create and get the Entity by id") {
      for {
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
        get <- EntityRepository.getById(in.id)
      } yield assert(out)(equalTo(in)) &&
        assert(get)(equalTo(in))
    },
    test("get the Entity by id that does not exist") {
      for {
        random <- ZIO.random
        _ <- random.setSeed(42L)
        id <- random.nextUUID
        get <- EntityRepository.getById(id).exit
      } yield assert(get)(dies(anything))
    }
  )

  private val createEntitySpec = suite("create the Entity spec")(
    test("create the Entity with random id and default wallet id") {
      for {
        in <- ZIO.succeed(Entity("test"))
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
      } yield assert(out)(equalTo(in)) && assert(out.walletId)(equalTo(Entity.ZeroWalletId))
    },
    test("create the Entity with random id and wallet id") {
      for {
        random <- ZIO.random
        _ <- random.setSeed(42L)
        walletId <- random.nextUUID
        in <- ZIO.succeed(Entity(name = "test", walletId = walletId))
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
      } yield assert(out)(equalTo(in)) && assert(out.walletId)(equalTo(walletId))
    },
    test("create the Entity with id and wallet id") {
      for {
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
      } yield assert(out)(equalTo(in)) &&
        assert(out.walletId)(equalTo(in.walletId)) &&
        assert(out.id)(equalTo(in.id)) &&
        assert(out.createdAt)(equalTo(in.createdAt)) &&
        assert(out.updatedAt)(equalTo(in.updatedAt))
    },
    test("create the Entity with the same id") {
      for {
        in <- createRandomEntity
        _ <- createAndStoreWallet(in)
        out <- EntityRepository.insert(in)
        exit <- EntityRepository.insert(in).exit
      } yield assert(exit)(dies(anything))
    },
    test("create the Entity with the walletId that doesn't exist") {
      for {
        in <- createRandomEntity
        // _ <- createAndStoreWallet(in) - the wallet is not created
        exit <- EntityRepository.insert(in).exit
      } yield assert(exit)(dies(anything))
    }
  )

}
