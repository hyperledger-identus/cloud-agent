package org.hyperledger.identus.shared.credentials

import zio.*

class CredentialVerifier(checks: Seq[VerificationCheck]):
  def verify(
      credential: RawCredential,
      ctx: VerifyContext,
      requestedChecks: Set[VerificationCheckType] = VerificationCheckType.values.toSet,
  ): IO[Throwable, VerificationResult] =
    for results <- ZIO.foreach(
        checks.filter(c => requestedChecks.contains(c.checkType) && c.appliesTo(credential))
      )(_.verify(credential, ctx))
    yield VerificationResult(results)
