package org.hyperledger.identus.shared.credentials

import zio.*

trait VerificationCheck:
  def checkType: VerificationCheckType
  def appliesTo(credential: RawCredential): Boolean
  def verify(credential: RawCredential, ctx: VerifyContext): IO[Throwable, CheckResult]
