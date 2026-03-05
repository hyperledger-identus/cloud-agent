package org.hyperledger.identus.credentials.anoncreds

import org.hyperledger.identus.shared.credentials.CredentialBuilder
import org.hyperledger.identus.shared.models.*
import zio.*

object AnonCredsBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("anoncreds-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("anoncreds")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer: TaskLayer[CredentialBuilder] =
    ZLayer.fromZIO(ZIO.fail(new RuntimeException(s"${id.value}: use CredentialBuilderRegistry instead")))
