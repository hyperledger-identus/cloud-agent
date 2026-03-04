package org.hyperledger.identus.shared.models

import zio.*
import zio.test.*
import zio.test.Assertion.*

object ModuleRegistrySpec extends ZIOSpecDefault:

  trait SimpleModule extends Module:
    type Config = Unit
    def defaultConfig = ()
    def enabled(config: Unit) = true
    def version = SemVer(1, 0, 0)

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
  )
