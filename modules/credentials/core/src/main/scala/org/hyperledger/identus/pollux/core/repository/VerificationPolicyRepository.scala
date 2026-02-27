package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.VerificationPolicy
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

trait VerificationPolicyRepository {

  def create(verificationPolicy: VerificationPolicy): URIO[WalletAccessContext, VerificationPolicy]

  def findById(id: UUID): URIO[WalletAccessContext, Option[VerificationPolicy]]

  def exists(id: UUID): URIO[WalletAccessContext, Boolean]

  def findHashById(id: UUID): URIO[WalletAccessContext, Option[Int]]

  def update(
      id: UUID,
      nonce: Int,
      verificationPolicy: VerificationPolicy
  ): URIO[WalletAccessContext, VerificationPolicy]

  def delete(id: UUID): URIO[WalletAccessContext, VerificationPolicy]

  def totalCount(): URIO[WalletAccessContext, Long]

  def filteredCount(nameOpt: Option[String]): URIO[WalletAccessContext, Long]

  def lookup(
      nameOpt: Option[String],
      offsetOpt: Option[Int],
      limitOpt: Option[Int]
  ): URIO[WalletAccessContext, List[VerificationPolicy]]
}
