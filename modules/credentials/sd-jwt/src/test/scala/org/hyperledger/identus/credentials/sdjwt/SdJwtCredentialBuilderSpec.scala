package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.credentials.*
import org.hyperledger.identus.shared.crypto.Ed25519PrivateKey
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object SdJwtCredentialBuilderSpec extends ZIOSpecDefault:

  /** Stub SDJwtService that returns a fixed compact credential */
  private object StubSdJwtService extends SDJwtService:
    def issueCredential(issuerKey: Ed25519PrivateKey, claims: String): CredentialCompact =
      CredentialCompact.unsafeFromCompact("eyHeader.eyPayload.signature~disclosure1~disclosure2~")

    def issueCredential(issuerKey: Ed25519PrivateKey, claims: String, holderJwk: String): CredentialCompact =
      issueCredential(issuerKey, claims)

    def createPresentation(sdjwt: CredentialCompact, claimsToDisclose: String): PresentationCompact =
      PresentationCompact.unsafeFromCompact("stub")

    def createPresentation(
        sdjwt: CredentialCompact,
        claimsToDisclose: String,
        nonce: String,
        aud: String,
        holderKey: Ed25519PrivateKey,
    ): PresentationCompact =
      PresentationCompact.unsafeFromCompact("stub")

  /** Stub IssuerKeyResolver that returns a fixed Ed25519 private key */
  private object StubKeyResolver extends SdJwtCredentialBuilder.IssuerKeyResolver:
    def resolve(keyRef: KeyRef): IO[Throwable, Ed25519PrivateKey] =
      ZIO.attempt {
        val keyPair = org.hyperledger.identus.shared.crypto.Apollo.default.ed25519.generateKeyPair
        keyPair.privateKey
      }

  private val claims: Json = """{"name":"Alice","degree":"CS"}""".fromJson[Json].toOption.get
  private val keyRef = KeyRef("test-key-id", SignatureAlgorithm.EdDSA)

  private val builder = SdJwtCredentialBuilder(
    sdJwtService = StubSdJwtService,
    keyResolver = StubKeyResolver,
  )

  override def spec = suite("SdJwtCredentialBuilder")(
    test("format is SDJWT") {
      assertTrue(builder.format == CredentialFormat.SDJWT)
    },
    test("supports VCDM 1.1") {
      assertTrue(builder.supportedDataModels.contains(DataModelType.VCDM_1_1))
    },
    test("steps are non-empty") {
      assertTrue(builder.steps.nonEmpty)
    },
    test("buildCredential produces SD-JWT compact format") {
      val ctx = BuildContext(
        claims = claims,
        format = CredentialFormat.SDJWT,
        dataModel = DataModelType.VCDM_1_1,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
      )
      for built <- builder.buildCredential(ctx)
      yield
        val sdjwtStr = new String(built.raw.data, "UTF-8")
        assertTrue(
          built.raw.format == CredentialFormat.SDJWT,
          sdjwtStr.contains("~"),
        )
    },
    test("buildCredential includes issuer claims") {
      val ctx = BuildContext(
        claims = claims,
        format = CredentialFormat.SDJWT,
        dataModel = DataModelType.VCDM_1_1,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
      )
      for built <- builder.buildCredential(ctx)
      yield assertTrue(built.metadata.asObject.isDefined)
    },
  )
