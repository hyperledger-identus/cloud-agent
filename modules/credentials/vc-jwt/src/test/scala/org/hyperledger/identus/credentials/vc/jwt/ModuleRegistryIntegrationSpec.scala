package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.core.codec.Vcdm11CodecModule
import org.hyperledger.identus.shared.crypto.{EdDsaSignerModule, Secp256k1SignerModule}
import org.hyperledger.identus.shared.models.*
import zio.*
import zio.test.*

object ModuleRegistryIntegrationSpec extends ZIOSpecDefault:

  private val allModules: Seq[Module] = Seq(
    EdDsaSignerModule,
    Secp256k1SignerModule,
    Vcdm11CodecModule,
    JwtBuilderModule,
  )

  override def spec = suite("ModuleRegistry Integration")(
    test("all modules register and dependencies are satisfied") {
      val registry = ModuleRegistry(allModules)
      for _ <- registry.validateDependencies
      yield assertTrue(registry.modules.size == 4)
    },
    test("resolves CredentialSigner to both signer modules") {
      val registry = ModuleRegistry(allModules)
      val signers = registry.resolve(Capability("CredentialSigner"))
      assertTrue(signers.size == 2)
    },
    test("resolves CredentialSigner(eddsa) to EdDSA only") {
      val registry = ModuleRegistry(allModules)
      val signers = registry.resolve(Capability("CredentialSigner", Some("eddsa")))
      assertTrue(
        signers.size == 1,
        signers.head.id == EdDsaSignerModule.id,
      )
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
    test("fails validation when signer is missing") {
      val incomplete = Seq(Vcdm11CodecModule, JwtBuilderModule)
      val registry = ModuleRegistry(incomplete)
      for result <- registry.validateDependencies.exit
      yield assertTrue(result.isFailure)
    },
    test("fails validation when codec is missing") {
      val incomplete = Seq(EdDsaSignerModule, JwtBuilderModule)
      val registry = ModuleRegistry(incomplete)
      for result <- registry.validateDependencies.exit
      yield assertTrue(result.isFailure)
    },
  )
