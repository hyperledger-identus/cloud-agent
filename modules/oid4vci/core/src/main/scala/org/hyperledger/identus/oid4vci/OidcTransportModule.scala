package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.shared.models.*

object OidcTransportModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("oidc-transport")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("ProtocolTransport", Some("oidc")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
