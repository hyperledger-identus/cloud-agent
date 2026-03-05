package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.shared.models.*
import zio.*

object JwtBuilderModule extends Module:
  type Config = Unit
  type Service = Unit

  val id: ModuleId = ModuleId("jwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("jwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer = ZLayer.succeed(())
