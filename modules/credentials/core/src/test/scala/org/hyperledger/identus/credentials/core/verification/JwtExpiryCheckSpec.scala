package org.hyperledger.identus.credentials.core.verification

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*

import java.time.Instant
import java.util.Base64

object JwtExpiryCheckSpec extends ZIOSpecDefault:

  private def makeJwt(exp: Option[Long]): RawCredential =
    val header = Base64.getUrlEncoder.withoutPadding().encodeToString("""{"alg":"EdDSA","typ":"JWT"}""".getBytes("UTF-8"))
    val payloadJson = exp match
      case Some(e) => s"""{"sub":"did:example:123","exp":$e}"""
      case None    => """{"sub":"did:example:123"}"""
    val payload = Base64.getUrlEncoder.withoutPadding().encodeToString(payloadJson.getBytes("UTF-8"))
    val signature = Base64.getUrlEncoder.withoutPadding().encodeToString("fake-sig".getBytes("UTF-8"))
    RawCredential(CredentialFormat.JWT, s"$header.$payload.$signature".getBytes("UTF-8"))

  override def spec = suite("JwtExpiryCheck")(
    test("checkType is Expiry") {
      assertTrue(JwtExpiryCheck.checkType == VerificationCheckType.Expiry)
    },
    test("applies to JWT credentials") {
      val cred = makeJwt(Some(9999999999L))
      assertTrue(JwtExpiryCheck.appliesTo(cred))
    },
    test("does not apply to AnonCreds") {
      val cred = RawCredential(CredentialFormat.AnonCreds, Array.emptyByteArray)
      assertTrue(!JwtExpiryCheck.appliesTo(cred))
    },
    test("passes when credential is not expired") {
      val futureExp = Instant.now().getEpochSecond + 3600
      val cred = makeJwt(Some(futureExp))
      val ctx = VerifyContext(currentTime = Instant.now())
      for result <- JwtExpiryCheck.verify(cred, ctx)
      yield assertTrue(result.success)
    },
    test("fails when credential is expired") {
      val pastExp = Instant.now().getEpochSecond - 3600
      val cred = makeJwt(Some(pastExp))
      val ctx = VerifyContext(currentTime = Instant.now())
      for result <- JwtExpiryCheck.verify(cred, ctx)
      yield assertTrue(!result.success) && assertTrue(result.detail.exists(_.contains("expired")))
    },
    test("passes when no exp claim present") {
      val cred = makeJwt(None)
      val ctx = VerifyContext(currentTime = Instant.now())
      for result <- JwtExpiryCheck.verify(cred, ctx)
      yield assertTrue(result.success)
    },
  )
