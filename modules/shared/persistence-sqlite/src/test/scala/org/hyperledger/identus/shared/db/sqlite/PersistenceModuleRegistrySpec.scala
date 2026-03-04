package org.hyperledger.identus.shared.db.sqlite

import org.hyperledger.identus.shared.db.PostgresPersistenceModule
import org.hyperledger.identus.shared.models.*
import zio.*
import zio.test.*

object PersistenceModuleRegistrySpec extends ZIOSpecDefault:

  override def spec = suite("Persistence ModuleRegistry")(
    test("both persistence providers register") {
      val registry = ModuleRegistry(Seq(PostgresPersistenceModule, SqlitePersistenceModule))
      for _ <- registry.validateDependencies
      yield assertTrue(registry.modules.size == 2)
    },
    test("resolves PersistenceProvider(postgresql)") {
      val registry = ModuleRegistry(Seq(PostgresPersistenceModule, SqlitePersistenceModule))
      val providers = registry.resolve(Capability("PersistenceProvider", Some("postgresql")))
      assertTrue(
        providers.size == 1,
        providers.head.id == PostgresPersistenceModule.id,
      )
    },
    test("resolves PersistenceProvider(sqlite)") {
      val registry = ModuleRegistry(Seq(PostgresPersistenceModule, SqlitePersistenceModule))
      val providers = registry.resolve(Capability("PersistenceProvider", Some("sqlite")))
      assertTrue(
        providers.size == 1,
        providers.head.id == SqlitePersistenceModule.id,
      )
    },
    test("resolves all PersistenceProvider implementations") {
      val registry = ModuleRegistry(Seq(PostgresPersistenceModule, SqlitePersistenceModule))
      val all = registry.resolve(Capability("PersistenceProvider"))
      assertTrue(all.size == 2)
    },
  )
