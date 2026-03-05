package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

object CredentialBuilderRegistrySpec extends ZIOSpecDefault:

  val stubJwtBuilder: CredentialBuilder = new CredentialBuilder:
    def format = CredentialFormat.JWT
    def supportedDataModels = Set(DataModelType.VCDM_1_1)
    def buildCredential(ctx: BuildContext) =
      ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.JWT, "jwt".getBytes), Json.Obj()))
    def steps = Seq.empty

  val stubSdJwtBuilder: CredentialBuilder = new CredentialBuilder:
    def format = CredentialFormat.SDJWT
    def supportedDataModels = Set(DataModelType.VCDM_1_1)
    def buildCredential(ctx: BuildContext) =
      ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.SDJWT, "sdjwt".getBytes), Json.Obj()))
    def steps = Seq.empty

  def spec = suite("CredentialBuilderRegistry")(
    test("resolves builder by format") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
        CredentialFormat.SDJWT -> stubSdJwtBuilder,
      ))
      assertTrue(
        registry.get(CredentialFormat.JWT).contains(stubJwtBuilder),
        registry.get(CredentialFormat.SDJWT).contains(stubSdJwtBuilder),
      )
    },
    test("returns None for unregistered format") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
      ))
      assertTrue(registry.get(CredentialFormat.AnonCreds).isEmpty)
    },
    test("formats returns all registered formats") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
        CredentialFormat.SDJWT -> stubSdJwtBuilder,
      ))
      assertTrue(registry.formats == Set(CredentialFormat.JWT, CredentialFormat.SDJWT))
    },
    test("empty registry returns None for all formats") {
      val registry = CredentialBuilderRegistry.empty
      assertTrue(
        registry.get(CredentialFormat.JWT).isEmpty,
        registry.get(CredentialFormat.SDJWT).isEmpty,
        registry.get(CredentialFormat.AnonCreds).isEmpty,
      )
    },
  )
