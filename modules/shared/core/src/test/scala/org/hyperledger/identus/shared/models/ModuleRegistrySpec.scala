package org.hyperledger.identus.shared.models

import zio.*
import zio.test.*
import zio.test.Assertion.*

object ModuleRegistrySpec extends ZIOSpecDefault:

  trait SimpleModule extends Module:
    type Config = Unit
    type Service = Unit
    def defaultConfig = ()
    def enabled(config: Unit) = true
    def version = SemVer(1, 0, 0)
    def layer = ZLayer.succeed(())

  object ProviderModule extends SimpleModule:
    val id = ModuleId("provider")
    val implements = Set(Capability("Signer", Some("eddsa")))
    val requires = Set.empty[Capability]

  object ConsumerModule extends SimpleModule:
    val id = ModuleId("consumer")
    val implements = Set(Capability("Builder", Some("jwt")))
    val requires = Set(Capability("Signer")) // any signer

  object UnsatisfiedModule extends SimpleModule:
    val id = ModuleId("unsatisfied")
    val implements = Set(Capability("Protocol", Some("v1")))
    val requires = Set(Capability("Transport", Some("keri"))) // nobody provides this

  def spec = suite("ModuleRegistry")(
    test("validates satisfied dependencies") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val result = registry.validateDependencies
      assertZIO(result)(isUnit)
    },
    test("rejects unsatisfied dependencies") {
      val registry = ModuleRegistry(Seq(ConsumerModule)) // no provider
      val result = registry.validateDependencies.exit
      assertZIO(result)(fails(anything))
    },
    test("rejects unsatisfied specific variant") {
      val registry = ModuleRegistry(Seq(ProviderModule, UnsatisfiedModule))
      val result = registry.validateDependencies.exit
      assertZIO(result)(fails(anything))
    },
    test("resolves capability to providing modules") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val signers = registry.resolve(Capability("Signer"))
      assertTrue(signers.map(_.id) == Seq(ProviderModule.id))
    },
    test("resolves with variant filter") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val eddsa = registry.resolve(Capability("Signer", Some("eddsa")))
      val es256 = registry.resolve(Capability("Signer", Some("es256")))
      assertTrue(eddsa.size == 1, es256.isEmpty)
    },
    test("fromAll filters disabled modules") {
      val registry = ModuleRegistry.fromAll(
        Seq(ProviderModule, ConsumerModule),
        disabled = Set(ConsumerModule.id),
      )
      assertTrue(
        registry.modules.size == 1,
        registry.modules.head.id == ProviderModule.id,
      )
    },
    test("fromAll respects Module.enabled") {
      object DisabledModule extends SimpleModule:
        val id = ModuleId("disabled")
        val implements = Set(Capability("Something"))
        val requires = Set.empty[Capability]
        override def enabled(config: Unit) = false

      val registry = ModuleRegistry.fromAll(Seq(ProviderModule, DisabledModule))
      assertTrue(
        registry.modules.size == 1,
        registry.modules.head.id == ProviderModule.id,
      )
    },
    test("report includes all module info") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val report = registry.report
      assertTrue(
        report.contains("2 modules loaded"),
        report.contains("provider"),
        report.contains("consumer"),
        report.contains("Signer(eddsa)"),
        report.contains("Builder(jwt)"),
      )
    },
    test("assembleBuilderRegistry collects builder modules by format") {
      import org.hyperledger.identus.shared.credentials.*
      import zio.json.ast.Json

      object JwtBuilderMod extends Module:
        type Config = Unit
        type Service = CredentialBuilder
        val id = ModuleId("jwt-builder")
        val implements = Set(Capability("CredentialBuilder", Some("jwt")))
        val requires = Set.empty[Capability]
        def defaultConfig = ()
        def enabled(config: Unit) = true
        def version = SemVer(1, 0, 0)
        def layer = ZLayer.succeed[CredentialBuilder](new CredentialBuilder:
          def format = CredentialFormat.JWT
          def supportedDataModels = Set(DataModelType.VCDM_1_1)
          def buildCredential(ctx: BuildContext) =
            ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.JWT, "jwt".getBytes), Json.Obj()))
          def steps = Seq.empty
        )

      object SdJwtBuilderMod extends Module:
        type Config = Unit
        type Service = CredentialBuilder
        val id = ModuleId("sdjwt-builder")
        val implements = Set(Capability("CredentialBuilder", Some("sdjwt")))
        val requires = Set.empty[Capability]
        def defaultConfig = ()
        def enabled(config: Unit) = true
        def version = SemVer(1, 0, 0)
        def layer = ZLayer.succeed[CredentialBuilder](new CredentialBuilder:
          def format = CredentialFormat.SDJWT
          def supportedDataModels = Set(DataModelType.VCDM_1_1)
          def buildCredential(ctx: BuildContext) =
            ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.SDJWT, "sdjwt".getBytes), Json.Obj()))
          def steps = Seq.empty
        )

      val registry = ModuleRegistry(Seq(ProviderModule, JwtBuilderMod, SdJwtBuilderMod))
      for
        builderRegistry <- registry.assembleBuilderRegistry
      yield assertTrue(
        builderRegistry.formats == Set(CredentialFormat.JWT, CredentialFormat.SDJWT),
        builderRegistry.get(CredentialFormat.JWT).map(_.format) == Some(CredentialFormat.JWT),
        builderRegistry.get(CredentialFormat.SDJWT).map(_.format) == Some(CredentialFormat.SDJWT),
        builderRegistry.get(CredentialFormat.AnonCreds).isEmpty,
      )
    },
  )
