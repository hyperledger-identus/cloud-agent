package org.hyperledger.identus.credentials.core.codec

import org.hyperledger.identus.shared.models.*
import zio.*

object Vcdm11CodecModule extends Module:
  type Config = Unit
  type Service = Unit

  val id: ModuleId = ModuleId("vcdm-1.1-codec")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("DataModelCodec", Some("vcdm-1.1")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer = ZLayer.succeed(())
