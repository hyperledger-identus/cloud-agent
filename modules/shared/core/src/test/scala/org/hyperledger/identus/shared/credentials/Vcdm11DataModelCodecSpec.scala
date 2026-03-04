package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object Vcdm11DataModelCodecSpec extends ZIOSpecDefault:

  private val codec = Vcdm11DataModelCodec

  private val sampleClaims: Json =
    """{"name":"Alice","age":30}""".fromJson[Json].toOption.get

  private val sampleMeta: Json =
    """{"issuer":"did:example:issuer","issuanceDate":"2026-01-01T00:00:00Z"}""".fromJson[Json].toOption.get

  override def spec = suite("Vcdm11DataModelCodec")(
    test("modelType is VCDM_1_1") {
      assertTrue(codec.modelType == DataModelType.VCDM_1_1)
    },
    test("encodeClaims wraps in W3C VC structure") {
      for
        encoded <- codec.encodeClaims(sampleClaims, sampleMeta)
        obj = encoded.asObject.get
      yield
        val context = obj.get("@context").flatMap(_.asArray)
        val tpe = obj.get("type").flatMap(_.asArray)
        val subject = obj.get("credentialSubject")
        assertTrue(
          context.exists(_.nonEmpty),
          tpe.exists(_.exists(_.asString.contains("VerifiableCredential"))),
          subject.contains(sampleClaims),
          obj.get("issuer").flatMap(_.asString).contains("did:example:issuer"),
          obj.get("issuanceDate").flatMap(_.asString).contains("2026-01-01T00:00:00Z"),
        )
    },
    test("decodeClaims extracts credentialSubject") {
      for
        encoded <- codec.encodeClaims(sampleClaims, sampleMeta)
        decoded <- codec.decodeClaims(RawCredential(CredentialFormat.JWT, encoded.toJson.getBytes("UTF-8")))
      yield assertTrue(decoded == sampleClaims)
    },
    test("validateStructure passes for valid VC") {
      for
        encoded <- codec.encodeClaims(sampleClaims, sampleMeta)
        raw = RawCredential(CredentialFormat.JWT, encoded.toJson.getBytes("UTF-8"))
        _ <- codec.validateStructure(raw)
      yield assertTrue(true)
    },
    test("validateStructure fails for missing @context") {
      val bad = """{"type":["VerifiableCredential"],"credentialSubject":{}}"""
      val raw = RawCredential(CredentialFormat.JWT, bad.getBytes("UTF-8"))
      for result <- codec.validateStructure(raw).exit
      yield assertTrue(result.isFailure)
    },
  )
