package org.hyperledger.identus.credentials.core.codec

import org.hyperledger.identus.shared.models.*

object Vcdm11CodecModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("vcdm-1.1-codec")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("DataModelCodec", Some("vcdm-1.1")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
