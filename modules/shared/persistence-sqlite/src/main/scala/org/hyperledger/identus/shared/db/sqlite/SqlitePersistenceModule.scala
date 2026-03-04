package org.hyperledger.identus.shared.db.sqlite

import org.hyperledger.identus.shared.models.*

object SqlitePersistenceModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("persistence-sqlite")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("PersistenceProvider", Some("sqlite")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
