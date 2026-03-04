package org.hyperledger.identus.shared.credentials

import zio.*
import zio.test.*

object CredentialVerifierSpec extends ZIOSpecDefault:
  object PassingCheck extends VerificationCheck:
    def checkType = VerificationCheckType.Expiry
    def appliesTo(c: RawCredential) = true
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.Expiry, success = true))

  object FailingCheck extends VerificationCheck:
    def checkType = VerificationCheckType.Signature
    def appliesTo(c: RawCredential) = true
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.Signature, success = false, Some("bad sig")))

  object JwtOnlyCheck extends VerificationCheck:
    def checkType = VerificationCheckType.ClaimsSchema
    def appliesTo(c: RawCredential) = c.format == CredentialFormat.JWT
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.ClaimsSchema, success = true))

  val jwtCred = RawCredential(CredentialFormat.JWT, Array.empty)
  val anonCred = RawCredential(CredentialFormat.AnonCreds, Array.empty)
  val ctx = VerifyContext()

  def spec = suite("CredentialVerifier")(
    test("all checks pass -> isValid") {
      val verifier = CredentialVerifier(Seq(PassingCheck))
      for result <- verifier.verify(jwtCred, ctx)
      yield assertTrue(result.isValid)
    },
    test("one check fails -> not isValid") {
      val verifier = CredentialVerifier(Seq(PassingCheck, FailingCheck))
      for result <- verifier.verify(jwtCred, ctx)
      yield assertTrue(!result.isValid, result.checks.size == 2)
    },
    test("non-applicable checks are skipped") {
      val verifier = CredentialVerifier(Seq(JwtOnlyCheck))
      for result <- verifier.verify(anonCred, ctx)
      yield assertTrue(result.checks.isEmpty, result.isValid)
    },
    test("filter by requested check types") {
      val verifier = CredentialVerifier(Seq(PassingCheck, FailingCheck))
      for result <- verifier.verify(jwtCred, ctx, requestedChecks = Set(VerificationCheckType.Expiry))
      yield assertTrue(result.isValid, result.checks.size == 1)
    },
  )
