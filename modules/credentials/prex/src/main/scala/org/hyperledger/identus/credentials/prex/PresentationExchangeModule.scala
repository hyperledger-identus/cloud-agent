package org.hyperledger.identus.credentials.prex

import org.hyperledger.identus.shared.models.*

object PresentationExchangeModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("presentation-exchange")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("PresentationExchange"),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
