package org.hyperledger.identus.shared.db.sqlite

import doobie.*
import doobie.implicits.*
import org.hyperledger.identus.shared.db.PersistenceType
import zio.*
import zio.interop.catz.*
import zio.test.*

import java.nio.file.Files

object SqlitePersistenceProviderSpec extends ZIOSpecDefault:

  private def withTempDb[A](f: SqlitePersistenceProvider => Task[A]): Task[A] =
    ZIO.scoped {
      for
        tmpFile <- ZIO.attempt(Files.createTempFile("identus-test-", ".db"))
        _ <- ZIO.addFinalizer(ZIO.attempt(Files.deleteIfExists(tmpFile)).ignore)
        provider <- SqlitePersistenceProvider.file(tmpFile.toString)
        result <- f(provider)
      yield result
    }

  override def spec = suite("SqlitePersistenceProvider")(
    test("providerType is SQLite") {
      withTempDb { provider =>
        ZIO.succeed(assertTrue(provider.providerType == PersistenceType.SQLite))
      }
    },
    test("migrate creates persistence_metadata table") {
      withTempDb { provider =>
        for
          _ <- provider.migrate
          result <- sql"SELECT value FROM persistence_metadata WHERE key = 'provider'"
            .query[String]
            .unique
            .transact(provider.transactor)
        yield assertTrue(result == "sqlite")
      }
    },
    test("transactor can execute queries") {
      withTempDb { provider =>
        for result <- sql"SELECT 1 + 1".query[Int].unique.transact(provider.transactor)
        yield assertTrue(result == 2)
      }
    },
  )
