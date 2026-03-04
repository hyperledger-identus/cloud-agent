package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.models.*
import zio.test.*

object SdJwtModuleRegistrySpec extends ZIOSpecDefault:

  override def spec = suite("SdJwtBuilderModule Registry")(
    test("module id is sdjwt-credential-builder") {
      assertTrue(SdJwtBuilderModule.id == ModuleId("sdjwt-credential-builder"))
    },
    test("implements CredentialBuilder(sdjwt)") {
      assertTrue(
        SdJwtBuilderModule.implements.contains(Capability("CredentialBuilder", Some("sdjwt")))
      )
    },
    test("requires DataModelCodec") {
      assertTrue(
        SdJwtBuilderModule.requires.exists(_.contract == "DataModelCodec")
      )
    },
    test("resolves in registry") {
      val registry = ModuleRegistry(Seq(SdJwtBuilderModule))
      val builders = registry.resolve(Capability("CredentialBuilder", Some("sdjwt")))
      assertTrue(
        builders.size == 1,
        builders.head.id == SdJwtBuilderModule.id,
      )
    },
  )
