package org.hyperledger.identus.pollux.prex

import org.hyperledger.identus.castor.core.model.did.DID
import org.hyperledger.identus.pollux.prex.PresentationSubmissionError.*
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.crypto.Apollo
import zio.*
import zio.json.{DecoderOps, JsonDecoder}
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

object PresentationSubmissionVerificationSpec extends ZIOSpecDefault {

  private def decodeUnsafe[T](json: String)(using JsonDecoder[T]): T = json.fromJson[T].toOption.get
  private def parseUnsafe(json: String): Json = json.fromJson[Json].toOption.get

  private val noopFormatVerification = ClaimFormatVerification(jwtVp = _ => ZIO.unit, jwtVc = _ => ZIO.unit)
  private val basePd: PresentationDefinition =
    decodeUnsafe[PresentationDefinition](
      """
        |{
        |  "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
        |  "input_descriptors": []
        |}
        """.stripMargin
    )
  private val basePs: PresentationSubmission =
    decodeUnsafe[PresentationSubmission](
      """
        |{
        |  "id": "a30e3b91-fb77-4d22-95fa-871689c322e2",
        |  "definition_id": "32f54163-7166-48f1-93d8-ff217bdb0653",
        |  "descriptor_map": []
        |}
        """.stripMargin
    )

  private def generateVcPayload(subject: Json): JwtCredentialPayload = {
    val iss = "did:example:ebfeb1f712ebc6f1c276e12ec21"
    val jwtCredentialNbf = Instant.parse("2010-01-01T00:00:00Z")
    JwtCredentialPayload(
      iss = iss,
      maybeSub = None,
      vc = JwtVc(
        `@context` = Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
        `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
        maybeCredentialSchema = None,
        credentialSubject = subject,
        maybeCredentialStatus = None,
        maybeRefreshService = None,
        maybeEvidence = None,
        maybeTermsOfUse = None,
        maybeValidFrom = None,
        maybeValidUntil = None,
        maybeIssuer = Some(iss)
      ),
      nbf = jwtCredentialNbf,
      aud = Set.empty,
      maybeExp = None,
      maybeJti = None
    )
  }

  private def generateVpPayload(vcs: Seq[VerifiableCredentialPayload]): JwtPresentationPayload = {
    val iss = "did:example:ebfeb1f712ebc6f1c276e12ec21"
    JwtPresentationPayload(
      iss = iss,
      vp = JwtVp(
        `@context` =
          Vector("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
        `type` = Vector("VerifiablePresentation"),
        verifiableCredential = vcs.toVector
      ),
      maybeNbf = None,
      aud = Vector.empty,
      maybeExp = None,
      maybeJti = None,
      maybeNonce = None,
    )
  }

  private def generateJwtVc(payload: JwtCredentialPayload): JWT = {
    val keyPair = Apollo.default.secp256k1.generateKeyPair
    val publicKey = keyPair.publicKey
    val privateKey = keyPair.privateKey
    val issuer = Issuer(
      DID.fromString(payload.iss).toOption.get,
      ES256KSigner(privateKey.toJavaPrivateKey, None),
      publicKey.toJavaPublicKey
    )
    JwtCredential.encodeJwt(payload, issuer)
  }

  private def generateJwtVp(payload: JwtPresentationPayload): JWT = {
    val keyPair = Apollo.default.secp256k1.generateKeyPair
    val publicKey = keyPair.publicKey
    val privateKey = keyPair.privateKey
    val issuer = Issuer(
      DID.fromString(payload.iss).toOption.get,
      ES256KSigner(privateKey.toJavaPrivateKey, None),
      publicKey.toJavaPublicKey
    )
    JwtPresentation.encodeJwt(payload, issuer)
  }

  private def assertSubmissionVerification(
      descriptorsJson: String,
      descriptorMapJson: String,
      jwt: JWT,
      formatVerification: ClaimFormatVerification = noopFormatVerification
  )(
      assertion: Assertion[Exit[PresentationSubmissionError, Unit]]
  ) = {
    val descriptors = decodeUnsafe[Seq[InputDescriptor]](descriptorsJson)
    val descriptorMap = decodeUnsafe[Seq[InputDescriptorMapping]](descriptorMapJson)
    val pd = basePd.copy(input_descriptors = descriptors)
    val ps = basePs.copy(descriptor_map = descriptorMap)
    for {
      result <- PresentationSubmissionVerification
        .verify(pd, ps, Json.Str(jwt.value))(formatVerification)
        .exit
    } yield assert(result)(assertion)
  }

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("PresentationSubmissionVerificationSpec")(
    test("descriptor and submission id not match") {
      val ps = basePs.copy(definition_id = "random-id")
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      for {
        result <- PresentationSubmissionVerification
          .verify(basePd, ps, Json.Str(jwtVc.value))(noopFormatVerification)
          .exit
      } yield assert(result)(failsWithA[InvalidSubmissionId])
    },
    test("empty descriptor and submission") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification("[]", "[]", jwtVc)(succeeds(anything))
    },
    test("one descriptor and corresponding submission") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {"id": "university_degree", "constraints": {}}
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor with no submission") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {"id": "university_degree", "constraints": {}}
          |]
          """.stripMargin,
        "[]",
        jwtVc
      )(failsWithA[SubmissionNotSatisfyInputDescriptors])
    },
    test("submission with no descriptor") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        "[]",
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(failsWithA[SubmissionNotSatisfyInputDescriptors])
    },
    test("descriptor with path verification") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {"path": ["$.vc.credentialSubject.name"]}
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor with multiple paths verification") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {"path": ["$.vc.credentialSubject.fullName", "$.vc.credentialSubject.name"]}
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor with path and filter verification") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "filter": {
          |            "type": "string",
          |            "const": "alice"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor with multiple paths and filter verification") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.fullName", "$.vc.credentialSubject.name"],
          |          "filter": {
          |            "type": "string",
          |            "const": "alice"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc,
      )(succeeds(anything))
    },
    test("descriptor and submission that dosn't satisfy the filter") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "filter": {
          |            "type": "string",
          |            "const": "bob"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(failsWithA[ClaimNotSatisfyInputConstraint])
    },
    test("descriptor with multiple fields verification") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice", "degree": "Finance"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "filter": {
          |            "type": "string",
          |            "const": "alice"
          |          }
          |        },
          |        {
          |          "path": ["$.vc.credentialSubject.degree"],
          |          "filter": {
          |            "type": "string",
          |            "const": "Finance"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("submission with nested path jwt_vc inside jwt_vp") {
      val vcPayload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(vcPayload)
      val vpPayload = generateVpPayload(Seq(JwtVerifiableCredentialPayload(jwtVc)))
      val jwtVp = generateJwtVp(vpPayload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "filter": {
          |            "type": "string",
          |            "const": "alice"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {
          |    "id": "university_degree",
          |    "format": "jwt_vp",
          |    "path": "$",
          |    "path_nested": {
          |      "id": "university_degree",
          |      "format": "jwt_vc",
          |      "path": "$.vp.verifiableCredential[0]"
          |    }
          |  }
          |]
          """.stripMargin,
        jwtVp
      )(succeeds(anything))
    },
    test("submission with nested_path having different id at each level should fail") {
      val vcPayload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(vcPayload)
      val vpPayload = generateVpPayload(Seq(JwtVerifiableCredentialPayload(jwtVc)))
      val jwtVp = generateJwtVp(vpPayload)
      assertSubmissionVerification(
        """[
          |  {"id": "university_degree", "constraints": {}}
          |]
          """.stripMargin,
        """[
          |  {
          |    "id": "university_degree",
          |    "format": "jwt_vp",
          |    "path": "$",
          |    "path_nested": {
          |      "id": "university_degree_2",
          |      "format": "jwt_vc",
          |      "path": "$.vp.verifiableCredential[0]"
          |    }
          |  }
          |]
          """.stripMargin,
        jwtVp
      )(failsWithA[InvalidNestedPathDescriptorId])
    },
    test("multiple descriptors with corresponding submission") {
      val vcPayload1 = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc1 = generateJwtVc(vcPayload1)
      val vcPayload2 = generateVcPayload(parseUnsafe("""{"vehicle_type": "car"}"""))
      val jwtVc2 = generateJwtVc(vcPayload2)
      val vpPayload = generateVpPayload(
        Seq(
          JwtVerifiableCredentialPayload(jwtVc1),
          JwtVerifiableCredentialPayload(jwtVc2)
        )
      )
      val jwtVp = generateJwtVp(vpPayload)
      assertSubmissionVerification(
        """[
          |  {"id": "university_degree", "constraints": {}},
          |  {"id": "driving_license", "constraints": {}}
          |]
          """.stripMargin,
        """[
          |  {
          |    "id": "university_degree",
          |    "format": "jwt_vp",
          |    "path": "$",
          |    "path_nested": {
          |      "id": "university_degree",
          |      "format": "jwt_vc",
          |      "path": "$.vp.verifiableCredential[0]"
          |    }
          |  },
          |  {
          |    "id": "driving_license",
          |    "format": "jwt_vp",
          |    "path": "$",
          |    "path_nested": {
          |      "id": "driving_license",
          |      "format": "jwt_vc",
          |      "path": "$.vp.verifiableCredential[1]"
          |    }
          |  }
          |]
          """.stripMargin,
        jwtVp
      )(succeeds(anything))
    },
    test("descriptor with optional field and submission that omit optional fields") {
      val payload = generateVcPayload(parseUnsafe("""{"gpa": 4.00}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "optional": true
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor with optional field and submission with optional fields that don't satisfy constraints") {
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {
          |    "id": "university_degree",
          |    "constraints": {
          |      "fields": [
          |        {
          |          "path": ["$.vc.credentialSubject.name"],
          |          "optional": true,
          |          "filter": {
          |            "type": "string",
          |            "const": "bob"
          |          }
          |        }
          |      ]
          |    }
          |  }
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc
      )(succeeds(anything))
    },
    test("descriptor and submission that fail the claim format decoding") {
      val formatVerification = noopFormatVerification.copy(
        jwtVc = _ => ZIO.fail("jwt is missing some required properties")
      )
      val payload = generateVcPayload(parseUnsafe("""{"name": "alice"}"""))
      val jwtVc = generateJwtVc(payload)
      assertSubmissionVerification(
        """[
          |  {"id": "university_degree", "constraints": {}}
          |]
          """.stripMargin,
        """[
          |  {"id": "university_degree", "format": "jwt_vc", "path": "$"}
          |]
          """.stripMargin,
        jwtVc,
        formatVerification
      )(failsWithA[ClaimFormatVerificationFailure])
    }
  )

}
