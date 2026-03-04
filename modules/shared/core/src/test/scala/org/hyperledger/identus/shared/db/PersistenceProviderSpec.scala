package org.hyperledger.identus.shared.db

import zio.*
import zio.test.*

object PersistenceProviderSpec extends ZIOSpecDefault:

  override def spec = suite("PersistenceProvider")(
    test("PersistenceType has PostgreSQL and SQLite variants") {
      assertTrue(
        PersistenceType.values.length == 2,
        PersistenceType.values.contains(PersistenceType.PostgreSQL),
        PersistenceType.values.contains(PersistenceType.SQLite),
      )
    },
    test("PersistenceProvider contract is implementable") {
      val stub = new PersistenceProvider:
        def providerType = PersistenceType.SQLite
        def transactor = null
        def migrate = ZIO.unit
      assertTrue(stub.providerType == PersistenceType.SQLite)
    },
  )
