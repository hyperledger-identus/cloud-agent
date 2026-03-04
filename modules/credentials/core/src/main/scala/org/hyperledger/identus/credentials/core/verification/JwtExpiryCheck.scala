package org.hyperledger.identus.credentials.core.verification

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.time.Instant
import java.util.Base64

object JwtExpiryCheck extends VerificationCheck:

  override def checkType: VerificationCheckType = VerificationCheckType.Expiry

  override def appliesTo(credential: RawCredential): Boolean =
    credential.format == CredentialFormat.JWT || credential.format == CredentialFormat.SDJWT

  override def verify(credential: RawCredential, ctx: VerifyContext): IO[Throwable, CheckResult] =
    ZIO.attempt {
      val jwtString = new String(credential.data, "UTF-8")
      val parts = jwtString.split('.')
      if parts.length < 2 then
        CheckResult(VerificationCheckType.Expiry, false, Some("Invalid JWT structure"))
      else
        val payloadJson = new String(Base64.getUrlDecoder.decode(parts(1)), "UTF-8")
        payloadJson.fromJson[Json] match
          case Left(err) =>
            CheckResult(VerificationCheckType.Expiry, false, Some(s"Failed to parse JWT payload: $err"))
          case Right(json) =>
            json.asObject.flatMap(_.get("exp")) match
              case None =>
                CheckResult(VerificationCheckType.Expiry, true, None)
              case Some(expJson) =>
                expJson.as[Long] match
                  case Left(_) =>
                    CheckResult(VerificationCheckType.Expiry, false, Some("Invalid exp claim"))
                  case Right(expEpoch) =>
                    val expInstant = Instant.ofEpochSecond(expEpoch)
                    if ctx.currentTime.isAfter(expInstant) then
                      CheckResult(VerificationCheckType.Expiry, false, Some(s"Credential expired at $expInstant"))
                    else
                      CheckResult(VerificationCheckType.Expiry, true, None)
    }
