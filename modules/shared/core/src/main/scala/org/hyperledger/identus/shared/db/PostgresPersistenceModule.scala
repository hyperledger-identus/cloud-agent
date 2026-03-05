package org.hyperledger.identus.shared.db

import org.hyperledger.identus.shared.models.*
import zio.*

object PostgresPersistenceModule extends Module:
  type Config = Unit
  type Service = Unit

  val id: ModuleId = ModuleId("persistence-postgresql")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("PersistenceProvider", Some("postgresql")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer = ZLayer.succeed(())
