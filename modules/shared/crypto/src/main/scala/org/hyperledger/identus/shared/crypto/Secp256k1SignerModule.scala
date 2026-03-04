package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.models.*

object Secp256k1SignerModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("secp256k1-signer")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialSigner", Some("es256k")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
