package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.core.codec.Vcdm11DataModelCodec
import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.util.Base64

object JwtCredentialBuilderSpec extends ZIOSpecDefault:

  /** Stub signer that just returns the payload as "signature" */
  private object StubSigner extends CredentialSigner:
    def algorithm: SignatureAlgorithm = SignatureAlgorithm.EdDSA
    def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]] =
      ZIO.succeed("stub-signature".getBytes("UTF-8"))
    def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean] =
      ZIO.succeed(true)

  private val claims: Json = """{"name":"Alice","degree":"CS"}""".fromJson[Json].toOption.get
  private val keyRef = KeyRef("test-key-id", SignatureAlgorithm.EdDSA)

  private val builder = JwtCredentialBuilder(
    codec = Vcdm11DataModelCodec,
    signer = StubSigner,
  )

  override def spec = suite("JwtCredentialBuilder")(
    test("format is JWT") {
      assertTrue(builder.format == CredentialFormat.JWT)
    },
    test("supports VCDM 1.1") {
      assertTrue(builder.supportedDataModels.contains(DataModelType.VCDM_1_1))
    },
    test("steps are non-empty") {
      assertTrue(builder.steps.nonEmpty)
    },
    test("buildCredential produces a JWT with 3 parts") {
      val ctx = BuildContext(
        claims = claims,
        format = CredentialFormat.JWT,
        dataModel = DataModelType.VCDM_1_1,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
        metadata = """{"issuanceDate":"2026-01-01T00:00:00Z"}""".fromJson[Json].toOption.get,
      )
      for
        built <- builder.buildCredential(ctx)
        jwtStr = new String(built.raw.data, "UTF-8")
        parts = jwtStr.split('.')
      yield
        assertTrue(
          built.raw.format == CredentialFormat.JWT,
          parts.length == 3,
        )
    },
    test("JWT payload contains W3C VC structure") {
      val ctx = BuildContext(
        claims = claims,
        format = CredentialFormat.JWT,
        dataModel = DataModelType.VCDM_1_1,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
        metadata = """{"issuanceDate":"2026-01-01T00:00:00Z"}""".fromJson[Json].toOption.get,
      )
      for
        built <- builder.buildCredential(ctx)
        jwtStr = new String(built.raw.data, "UTF-8")
        payloadB64 = jwtStr.split('.')(1)
        payloadJson = new String(Base64.getUrlDecoder.decode(payloadB64), "UTF-8")
        json = payloadJson.fromJson[Json].toOption.get
        obj = json.asObject.get
      yield
        assertTrue(
          obj.get("credentialSubject").isDefined,
          obj.get("issuer").flatMap(_.asString).contains("did:example:issuer"),
        )
    },
  )
