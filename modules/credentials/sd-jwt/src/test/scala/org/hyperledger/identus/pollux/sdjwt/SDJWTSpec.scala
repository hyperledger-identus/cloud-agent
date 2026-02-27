package org.hyperledger.identus.pollux.sdjwt

import org.hyperledger.identus.pollux.sdjwt.*
import org.hyperledger.identus.shared.crypto.*
import zio.*
import zio.json.*
import zio.test.*
import zio.test.Assertion.*

def ISSUER_KEY = IssuerPrivateKey.fromEcPem(
  """-----BEGIN PRIVATE KEY-----
    |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgUr2bNKuBPOrAaxsR
    |nbSH6hIhmNTxSGXshDSUD1a1y7ihRANCAARvbx3gzBkyPDz7TQIbjF+ef1IsxUwz
    |X1KWpmlVv+421F7+c1sLqGk4HUuoVeN8iOoAcE547pJhUEJyf5Asc6pP
    |-----END PRIVATE KEY-----
    |""".stripMargin
)
def ISSUER_KEY1 = IssuerPrivateKey.fromEcPem(
  """-----BEGIN PRIVATE KEY-----
    |kh0+W08hda6NpaytvEeyGdwjPwgJOfXmihEcAvztt5c=
    |-----END PRIVATE KEY-----""".stripMargin
)

def ISSUER_KEY_PUBLIC = IssuerPublicKey.fromPem(
  """-----BEGIN PUBLIC KEY-----
    |MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb28d4MwZMjw8+00CG4xfnn9SLMVM
    |M19SlqZpVb/uNtRe/nNbC6hpOB1LqFXjfIjqAHBOeO6SYVBCcn+QLHOqTw==
    |-----END PUBLIC KEY-----
    |""".stripMargin
)

def HOLDER_KEY = HolderPrivateKey.fromEcPem(
  """-----BEGIN PRIVATE KEY-----
    |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg5K5SCos8zf9zRemG
    |GUl6yfok+/NiiryNZsvANWMhF+KhRANCAARMIARHX1m+7c4cXiPhbi99JWgcg/Ug
    |uKUOWzu8J4Z6Z2cY4llm2TEBh1VilUOIW0iIq7FX7nnAhOreI0/Rdh2U
    |-----END PRIVATE KEY-----
    |""".stripMargin
)

def HOLDER_KEY_JWK_PUBLIC = HolderPublicKey.fromJWT(
  """{
    |  "kty": "EC",
    |  "crv": "P-256",
    |  "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
    |  "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    |}""".stripMargin
)

def CLAIMS =
  """{
    |  "sub": "did:example:holder",
    |  "iss": "did:example:issuer",
    |  "iat": 1683000000,
    |  "exp": 1883000000,
    |  "address": {
    |    "street_address": "Schulstr. 12",
    |    "locality": "Schulpforta",
    |    "region": "Sachsen-Anhalt",
    |    "country": "DE"
    |  }
    |}""".stripMargin

def CLAIMS_WITHOUT_SUB_IAT =
  """{
    |  "iss": "did:example:issuer",
    |  "exp": 1883000000,
    |  "address": {
    |    "street_address": "Schulstr. 12",
    |    "locality": "Schulpforta",
    |    "region": "Sachsen-Anhalt",
    |    "country": "DE"
    |  }
    |}""".stripMargin

def CLAIMS_QUERY =
  """{
    |  "address": {
    |    "country": {}
    |  }
    |}""".stripMargin

def CLAIMS_PRESENTED =
  """{
    |  "sub": "did:example:holder",
    |  "iss": "did:example:issuer",
    |  "iat": 1683000000,
    |  "exp": 1883000000,
    |  "address": {
    |    "region": "Sachsen-Anhalt",
    |    "country": "DE"
    |  }
    |}""".stripMargin

def FAlSE_CLAIMS_PRESENTED =
  """{
    |  "sub": "did:example:holder",
    |  "iss": "did:example:issuer",
    |  "iat": 1683000000,
    |  "exp": 1883000000,
    |  "address": {
    |    "region": "Sachsen-Anhalt",
    |    "country": "PT"
    |  }
    |}""".stripMargin

object SDJWTSpec extends ZIOSpecDefault {

  override def spec = suite("SDJWTRawSpec")(
    test("CredentialCompact") {
      val credentialExample =
        "eyJhbGciOiJFUzI1NiJ9.eyJfc2QiOlsiMXRJNHZLQ2R3ald5aExxT0lkbTloUHYxNFo0ellEVlRiekgzUWd2S3ctTSIsIkM2alFVaDJiYzh3YTB6dHJiVFNnMUJxQkNMQ2tJSk5lU3ZuSkoyZ2NDc2MiXSwiX3NkX2FsZyI6InNoYS0yNTYiLCJpc3MiOiJkaWQ6ZXhhbXBsZTppc3N1ZXIiLCJpYXQiOjE2ODMwMDAwMDAsImV4cCI6MTg4MzAwMDAwMCwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCJ5IjoiWnhqaVdXYlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.MpRZOLNaCCs4_h7Injbp4MfiGfnieu2aMFG1cbD8KM_NyKwRnPrcMeSHkUGhDoYqTPfOFdvbLeWefWQr-u3hRw~WyJhbXZPbWxTMFJDajhwakQ0d2FpWVNBIiwgInN1YiIsICJkaWQ6ZXhhbXBsZTpob2xkZXIiXQ~WyJ1ZW9zVjRTMzVjMG1JbGFXZld5enp3IiwgInN0cmVldF9hZGRyZXNzIiwgIlNjaHVsc3RyLiAxMiJd~WyI4c3ZDaG1EcFlhUl9RSXFRVXY1OTF3IiwgImxvY2FsaXR5IiwgIlNjaHVscGZvcnRhIl0~WyJvbEw4dnV1LVgtVkR2QjR6R1pBS0p3IiwgInJlZ2lvbiIsICJTYWNoc2VuLUFuaGFsdCJd~WyJweV9tZmdZSjNrVDIwRkZDZkdMR2N3IiwgImNvdW50cnkiLCAiREUiXQ~WyJPY2RLUFNYT2VtVTNITlNqRmVkYldRIiwgImFkZHJlc3MiLCB7Il9zZCI6WyIxVlRyYzU0LVJmV3FEdnM4ay0yT2NDZmJnbWwxWWg5TTR4X3hJTHA2Qk9jIiwiMkJCSjhMR1M0UUZsYUZMNGY4UzFWUDhvaG83dzJQUkVlUHJwelRVQktMOCIsIkEwWnBkSUhrVjVrX3N4b3hMYkZoU3B3UEZFcEJUeWdoM0h2SnNzNmxRS0EiLCJzT3lnSmQwMWE2dzV1YUszMVJEUXF1dTk5VTJ2TENxWHNyd2lodHBvTklJIl19XQ~"
      val c = CredentialCompact.unsafeFromCompact(credentialExample)

      assertTrue(c.jwtHeader == "eyJhbGciOiJFUzI1NiJ9") &&
      assertTrue(
        c.jwtPayload ==
          "eyJfc2QiOlsiMXRJNHZLQ2R3ald5aExxT0lkbTloUHYxNFo0ellEVlRiekgzUWd2S3ctTSIsIkM2alFVaDJiYzh3YTB6dHJiVFNnMUJxQkNMQ2tJSk5lU3ZuSkoyZ2NDc2MiXSwiX3NkX2FsZyI6InNoYS0yNTYiLCJpc3MiOiJkaWQ6ZXhhbXBsZTppc3N1ZXIiLCJpYXQiOjE2ODMwMDAwMDAsImV4cCI6MTg4MzAwMDAwMCwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IlRDQUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCJ5IjoiWnhqaVdXYlpNUUdIVldLVlE0aGJTSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0"
      ) &&
      assertTrue(
        c.jwtSignature == "MpRZOLNaCCs4_h7Injbp4MfiGfnieu2aMFG1cbD8KM_NyKwRnPrcMeSHkUGhDoYqTPfOFdvbLeWefWQr-u3hRw"
      ) &&
      assertTrue(
        c.kbJWT.isEmpty
      ) &&
      assertTrue(
        Seq(
          "WyJhbXZPbWxTMFJDajhwakQ0d2FpWVNBIiwgInN1YiIsICJkaWQ6ZXhhbXBsZTpob2xkZXIiXQ",
          "WyJ1ZW9zVjRTMzVjMG1JbGFXZld5enp3IiwgInN0cmVldF9hZGRyZXNzIiwgIlNjaHVsc3RyLiAxMiJd",
          "WyI4c3ZDaG1EcFlhUl9RSXFRVXY1OTF3IiwgImxvY2FsaXR5IiwgIlNjaHVscGZvcnRhIl0",
          "WyJvbEw4dnV1LVgtVkR2QjR6R1pBS0p3IiwgInJlZ2lvbiIsICJTYWNoc2VuLUFuaGFsdCJd",
          "WyJweV9tZmdZSjNrVDIwRkZDZkdMR2N3IiwgImNvdW50cnkiLCAiREUiXQ",
          "WyJPY2RLUFNYT2VtVTNITlNqRmVkYldRIiwgImFkZHJlc3MiLCB7Il9zZCI6WyIxVlRyYzU0LVJmV3FEdnM4ay0yT2NDZmJnbWwxWWg5TTR4X3hJTHA2Qk9jIiwiMkJCSjhMR1M0UUZsYUZMNGY4UzFWUDhvaG83dzJQUkVlUHJwelRVQktMOCIsIkEwWnBkSUhrVjVrX3N4b3hMYkZoU3B3UEZFcEJUeWdoM0h2SnNzNmxRS0EiLCJzT3lnSmQwMWE2dzV1YUszMVJEUXF1dTk5VTJ2TENxWHNyd2lodHBvTklJIl19XQ",
        ) == c.disclosures
      )
    },
    test("issue credential") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS, HOLDER_KEY_JWK_PUBLIC)
      assertTrue(!credential.compact.isEmpty())
    },
    test("make presentation") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS)
      val presentation = SDJWT.createPresentation(credential, CLAIMS_PRESENTED)
      assertTrue(!presentation.compact.isEmpty())
    },
    test("getVerifiedClaims presentation") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS)
      val presentation = SDJWT.createPresentation(credential, CLAIMS_QUERY)
      val ret = SDJWT.getVerifiedClaims(ISSUER_KEY_PUBLIC, presentation)
      assertTrue(
        """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
          .map(expected => ret == SDJWT.ValidClaims(expected))
          .getOrElse(false)
      )
    },
    test("issue credential without sub & iat and getVerifiedClaims") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS_WITHOUT_SUB_IAT)
      // verfier asking to disclose
      val presentation = SDJWT.createPresentation(credential, CLAIMS_QUERY)
      val ret = SDJWT.getVerifiedClaims(ISSUER_KEY_PUBLIC, presentation)
      assertTrue(
        """{"iss":"did:example:issuer","exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
          .map(expected => ret == SDJWT.ValidClaims(expected))
          .getOrElse(false)
      )
    },
    test("verify presentation") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS)
      val presentation = SDJWT.createPresentation(credential, CLAIMS_PRESENTED)
      val ret = SDJWT.verifyAndComparePresentation(ISSUER_KEY_PUBLIC, presentation, CLAIMS_PRESENTED)
      assertTrue(ret == SDJWT.ValidAnyMatch)
    },
    test("fail to verify false claimes presentation") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS)
      val presentation = SDJWT.createPresentation(credential, CLAIMS_PRESENTED)
      val ret = SDJWT.verifyAndComparePresentation(ISSUER_KEY_PUBLIC, presentation, FAlSE_CLAIMS_PRESENTED)
      assertTrue(ret == SDJWT.InvalidClaims)
    },

    // presentation challenge
    test("make presentation with holder presentation challenge") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS, HOLDER_KEY_JWK_PUBLIC)
      val presentation = SDJWT.createPresentation(
        credential,
        CLAIMS_PRESENTED,
        "nonce123456789",
        "did:example:verifier",
        HOLDER_KEY
      )
      assert(presentation.compact)(isNonEmptyString)
      // Assertion { TestArrow.make[PresentationCompact, String] { a => TestTrace.succeed(a.value) } >>> isEmptyString.arrow }
    },
    test("verify presentation with holder presentation challenge") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS, HOLDER_KEY_JWK_PUBLIC)
      val presentation = SDJWT.createPresentation(
        credential,
        CLAIMS_PRESENTED,
        "nonce123456789",
        "did:example:verifier",
        HOLDER_KEY
      )
      val ret = SDJWT.verifyAndComparePresentation(
        key = ISSUER_KEY_PUBLIC,
        presentation = presentation,
        claims = CLAIMS_PRESENTED,
        expectedNonce = "nonce123456789",
        expectedAud = "did:example:verifier",
      )
      assertTrue(ret == SDJWT.ValidAnyMatch)
    },
    test("fail to verify presentation with holder presentation challenge") {
      val credential = SDJWT.issueCredential(ISSUER_KEY, CLAIMS)
      val presentation = SDJWT.createPresentation(
        sdjwt = credential,
        claimsToDisclose = CLAIMS_PRESENTED,
        nonce = "nonce123456789",
        aud = "did:example:verifier",
        holderKey = HOLDER_KEY
      )
      val ret = SDJWT.verifyAndComparePresentation(
        ISSUER_KEY_PUBLIC,
        presentation,
        FAlSE_CLAIMS_PRESENTED,
      )
      assertTrue(ret == SDJWT.InvalidClaims)
    },
    test("IssuerPrivateKey from a key type ED25519") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      IssuerPrivateKey(ed25519KeyPair.privateKey) // doesn't throw exceptions
      IssuerPublicKey(ed25519KeyPair.publicKey) // doesn't throw exceptions
      assertTrue(true)
    },
    test("HolderPrivateKey from a key type ED25519") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      HolderPrivateKey(ed25519KeyPair.privateKey) // doesn't throw exceptions
      assertTrue(true)
    },
    test("Flow with key type ED25519 no presentation challenge") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val issuerKey = IssuerPrivateKey(ed25519KeyPair.privateKey)
      val issuerPublicKey = IssuerPublicKey(ed25519KeyPair.publicKey)

      val credential = SDJWT.issueCredential(issuerKey, CLAIMS)
      // verifer addres
      val presentation = SDJWT.createPresentation(credential, CLAIMS_PRESENTED)
      val ret = SDJWT.verifyAndComparePresentation(issuerPublicKey, presentation, CLAIMS_PRESENTED)
      assertTrue(ret == SDJWT.ValidAnyMatch)
    },
    test("Flow with key type ED25519 with presentation challenge") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val privateKey = ed25519KeyPair.privateKey
      val issuerKey = IssuerPrivateKey(privateKey)
      val issuerPublicKey = IssuerPublicKey(ed25519KeyPair.publicKey)

      val holderEd25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val holderKey = HolderPrivateKey(holderEd25519KeyPair.privateKey)
      val holderKeyPublic = HolderPublicKey(holderEd25519KeyPair.publicKey)
      val credential = SDJWT.issueCredential(issuerKey, CLAIMS, holderKeyPublic)

      val presentation = SDJWT.createPresentation(
        sdjwt = credential,
        claimsToDisclose = CLAIMS_PRESENTED,
        nonce = "nonce123456789",
        aud = "did:example:verifier",
        holderKey = holderKey
      )
      val ret = SDJWT.verifyAndComparePresentation(
        key = issuerPublicKey,
        presentation = presentation,
        claims = CLAIMS_PRESENTED,
        expectedNonce = "nonce123456789",
        expectedAud = "did:example:verifier"
      )
      assertTrue(ret == SDJWT.ValidAnyMatch)
    },
    test("Flow with key type ED25519 with presentation challenge fail") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val privateKey = ed25519KeyPair.privateKey
      val issuerKey = IssuerPrivateKey(privateKey)
      val issuerPublicKey = IssuerPublicKey(ed25519KeyPair.publicKey)

      val holderEd25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      // val holderKey = HolderPrivateKey(holderEd25519KeyPair.privateKey)
      val holderKeyPublic = HolderPublicKey(holderEd25519KeyPair.publicKey)
      val credential = SDJWT.issueCredential(issuerKey, CLAIMS, holderKeyPublic)

      val failHolderEd25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val failHolderKey = HolderPrivateKey(failHolderEd25519KeyPair.privateKey)

      val presentation = SDJWT.createPresentation(
        sdjwt = credential,
        claimsToDisclose = CLAIMS_PRESENTED,
        nonce = "nonce123456789",
        aud = "did:example:verifier",
        holderKey = failHolderKey
      )
      val ret = SDJWT.verifyAndComparePresentation(
        key = issuerPublicKey,
        presentation = presentation,
        claims = CLAIMS_PRESENTED,
        expectedNonce = "nonce123456789",
        expectedAud = "did:example:verifier"
      )
      assertTrue(ret == SDJWT.InvalidSignature)
    },
    // methods
    test("get iss field from PresentationCompact") {
      val ed25519KeyPair = KmpEd25519KeyOps.generateKeyPair
      val issuerKey = IssuerPrivateKey(ed25519KeyPair.privateKey)
      IssuerPublicKey(ed25519KeyPair.publicKey)

      val credential = SDJWT.issueCredential(issuerKey, CLAIMS)
      val presentation = SDJWT.createPresentation(credential, CLAIMS_PRESENTED)
      // val ret = SDJWT.verifyPresentation(issuerPublicKey, presentation, CLAIMS_PRESENTED)
      assert(presentation.iss)(isRight(equalTo("did:example:issuer")))
    },
  )

}
