package org.hyperledger.identus.server

import org.hyperledger.identus.shared.credentials.CredentialBuilderRegistry
import zio.*

object CredentialBuilderRegistryLive:
  val layer: ULayer[CredentialBuilderRegistry] =
    ZLayer.succeed(CredentialBuilderRegistry.empty)
