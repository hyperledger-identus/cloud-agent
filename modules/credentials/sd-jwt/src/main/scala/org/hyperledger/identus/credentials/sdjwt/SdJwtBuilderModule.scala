package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.models.*
import zio.*

object SdJwtBuilderModule extends Module:
  type Config = Unit
  type Service = Unit

  val id: ModuleId = ModuleId("sdjwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("sdjwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer = ZLayer.succeed(())
