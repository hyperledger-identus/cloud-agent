package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.{VerificationPolicy, VerificationPolicyConstraint}
import org.hyperledger.identus.pollux.core.model.error.VerificationPolicyError
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

trait VerificationPolicyService {

  def create(
      name: String,
      description: String,
      constraints: Seq[VerificationPolicyConstraint] = Seq.empty
  ): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy]

  def find(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, Option[VerificationPolicy]]

  def get(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy]

  def update(
      id: UUID,
      nonce: Int,
      verificationPolicy: VerificationPolicy
  ): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy]

  def delete(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy]

  def totalCount(): ZIO[WalletAccessContext, VerificationPolicyError, Long]

  def filteredCount(name: Option[String]): ZIO[WalletAccessContext, VerificationPolicyError, Long]

  def lookup(
      name: Option[String],
      offset: Option[Int],
      limit: Option[Int]
  ): ZIO[WalletAccessContext, VerificationPolicyError, List[VerificationPolicy]]
}
