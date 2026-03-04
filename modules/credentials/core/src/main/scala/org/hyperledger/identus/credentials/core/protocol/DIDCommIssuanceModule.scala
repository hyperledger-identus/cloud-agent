package org.hyperledger.identus.credentials.core.protocol

import org.hyperledger.identus.shared.models.*

object DIDCommIssuanceModule extends Module:
  type Config = Unit

  val id: ModuleId = ModuleId("didcomm-issuance-v3")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("IssuanceProtocol", Some("didcomm-v3")),
  )

  val requires: Set[Capability] = Set(
    Capability("CredentialBuilder"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
