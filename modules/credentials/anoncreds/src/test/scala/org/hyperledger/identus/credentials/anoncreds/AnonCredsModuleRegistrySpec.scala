package org.hyperledger.identus.credentials.anoncreds

import org.hyperledger.identus.shared.models.*
import zio.test.*

object AnonCredsModuleRegistrySpec extends ZIOSpecDefault:

  override def spec = suite("AnonCredsBuilderModule Registry")(
    test("module id is anoncreds-credential-builder") {
      assertTrue(AnonCredsBuilderModule.id == ModuleId("anoncreds-credential-builder"))
    },
    test("implements CredentialBuilder(anoncreds)") {
      assertTrue(
        AnonCredsBuilderModule.implements.contains(Capability("CredentialBuilder", Some("anoncreds")))
      )
    },
    test("requires nothing (self-contained)") {
      assertTrue(AnonCredsBuilderModule.requires.isEmpty)
    },
    test("resolves in registry") {
      val registry = ModuleRegistry(Seq(AnonCredsBuilderModule))
      val builders = registry.resolve(Capability("CredentialBuilder", Some("anoncreds")))
      assertTrue(
        builders.size == 1,
        builders.head.id == AnonCredsBuilderModule.id,
      )
    },
  )
