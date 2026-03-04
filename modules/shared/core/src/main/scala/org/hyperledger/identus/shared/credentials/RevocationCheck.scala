package org.hyperledger.identus.shared.credentials

trait RevocationCheck extends VerificationCheck:
  def mechanism: RevocationMechanism
  override def checkType: VerificationCheckType = VerificationCheckType.Revocation
