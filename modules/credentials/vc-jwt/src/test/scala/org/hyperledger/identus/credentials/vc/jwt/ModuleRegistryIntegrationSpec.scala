package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.core.codec.Vcdm11CodecModule
import org.hyperledger.identus.credentials.core.protocol.{DIDCommIssuanceModule, DIDCommPresentationModule}
import org.hyperledger.identus.shared.db.PostgresPersistenceModule
import org.hyperledger.identus.shared.models.*
import zio.*
import zio.test.*

object ModuleRegistryIntegrationSpec extends ZIOSpecDefault:

  private val allModules: Seq[Module] = Seq(
    Vcdm11CodecModule,
    JwtBuilderModule,
    DIDCommIssuanceModule,
    DIDCommPresentationModule,
    PostgresPersistenceModule,
  )

  override def spec = suite("ModuleRegistry Integration")(
    test("all modules register and dependencies are satisfied") {
      val registry = ModuleRegistry(allModules)
      for _ <- registry.validateDependencies
      yield assertTrue(registry.modules.size == 5)
    },
    test("all module ids are unique") {
      val ids = allModules.map(_.id)
      assertTrue(ids.distinct.size == ids.size)
    },
    test("resolves CredentialBuilder(jwt) to JwtBuilderModule") {
      val registry = ModuleRegistry(allModules)
      val builders = registry.resolve(Capability("CredentialBuilder", Some("jwt")))
      assertTrue(
        builders.size == 1,
        builders.head.id == JwtBuilderModule.id,
      )
    },
    test("resolves DataModelCodec(vcdm-1.1) to Vcdm11CodecModule") {
      val registry = ModuleRegistry(allModules)
      val codecs = registry.resolve(Capability("DataModelCodec", Some("vcdm-1.1")))
      assertTrue(
        codecs.size == 1,
        codecs.head.id == Vcdm11CodecModule.id,
      )
    },
    test("resolves IssuanceProtocol(didcomm-v3) to DIDCommIssuanceModule") {
      val registry = ModuleRegistry(allModules)
      val protocols = registry.resolve(Capability("IssuanceProtocol", Some("didcomm-v3")))
      assertTrue(
        protocols.size == 1,
        protocols.head.id == DIDCommIssuanceModule.id,
      )
    },
    test("resolves PresentationProtocol(didcomm-v3) to DIDCommPresentationModule") {
      val registry = ModuleRegistry(allModules)
      val protocols = registry.resolve(Capability("PresentationProtocol", Some("didcomm-v3")))
      assertTrue(
        protocols.size == 1,
        protocols.head.id == DIDCommPresentationModule.id,
      )
    },
    test("resolves PersistenceProvider(postgresql)") {
      val registry = ModuleRegistry(allModules)
      val providers = registry.resolve(Capability("PersistenceProvider", Some("postgresql")))
      assertTrue(
        providers.size == 1,
        providers.head.id == PostgresPersistenceModule.id,
      )
    },
    test("fails validation when codec is missing") {
      val incomplete = Seq(JwtBuilderModule)
      val registry = ModuleRegistry(incomplete)
      for result <- registry.validateDependencies.exit
      yield assertTrue(result.isFailure)
    },
    test("fromAll filters disabled modules") {
      val registry = ModuleRegistry.fromAll(allModules, disabled = Set(JwtBuilderModule.id))
      assertTrue(
        !registry.modules.exists(_.id == JwtBuilderModule.id),
        registry.modules.size == 4,
      )
    },
    test("report contains all module names") {
      val registry = ModuleRegistry(allModules)
      val report = registry.report
      assertTrue(allModules.forall(m => report.contains(m.id.value)))
    },
  )
