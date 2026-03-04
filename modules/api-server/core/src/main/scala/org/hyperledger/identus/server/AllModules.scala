package org.hyperledger.identus.server

import org.hyperledger.identus.credentials.core.codec.Vcdm11CodecModule
import org.hyperledger.identus.credentials.core.protocol.{DIDCommIssuanceModule, DIDCommPresentationModule}
import org.hyperledger.identus.credentials.vc.jwt.JwtBuilderModule
import org.hyperledger.identus.oid4vci.{OidcIssuanceModule, OidcPresentationModule}
import org.hyperledger.identus.shared.db.PostgresPersistenceModule
import org.hyperledger.identus.shared.models.*

object AllModules:

  val all: Seq[Module] = Seq(
    // Credential data model codecs
    Vcdm11CodecModule,
    // Credential builders
    JwtBuilderModule,
    // Protocol adapters — DIDComm
    DIDCommIssuanceModule,
    DIDCommPresentationModule,
    // Protocol adapters — OIDC
    OidcIssuanceModule,
    OidcPresentationModule,
    // Persistence
    PostgresPersistenceModule,
  )

  def registry(disabled: Set[ModuleId] = Set.empty): ModuleRegistry =
    ModuleRegistry.fromAll(all, disabled)
