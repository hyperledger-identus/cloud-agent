package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.models.*

object EdDsaSignerModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("eddsa-signer")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialSigner", Some("eddsa")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
