package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.{VerificationPolicy, VerificationPolicyConstraint}
import org.hyperledger.identus.pollux.core.model.error.VerificationPolicyError
import org.hyperledger.identus.pollux.core.model.error.VerificationPolicyError.NotFoundError
import org.hyperledger.identus.pollux.core.repository.VerificationPolicyRepository
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

object VerificationPolicyServiceImpl {
  val layer: URLayer[VerificationPolicyRepository, VerificationPolicyService] =
    ZLayer.fromFunction(VerificationPolicyServiceImpl(_))
}

class VerificationPolicyServiceImpl(
    repository: VerificationPolicyRepository
) extends VerificationPolicyService {

  override def create(
      name: String,
      description: String,
      constraints: Seq[VerificationPolicyConstraint]
  ): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy] = {
    for {
      verificationPolicy <- VerificationPolicy.make(name, description, constraints)
      createdVerificationPolicy <- repository.create(verificationPolicy)
    } yield createdVerificationPolicy
  }

  override def find(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, Option[VerificationPolicy]] =
    repository
      .findById(id)

  override def get(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy] = {
    repository
      .findById(id)
      .flatMap {
        case None        => ZIO.fail(NotFoundError(id))
        case Some(value) => ZIO.succeed(value)
      }
  }

  override def update(
      id: UUID,
      nonce: Int,
      verificationPolicy: VerificationPolicy
  ): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy] =
    for {
      _ <- get(id)
      result <- repository.update(id, nonce, verificationPolicy)
    } yield result

  override def delete(id: UUID): ZIO[WalletAccessContext, VerificationPolicyError, VerificationPolicy] =
    for {
      _ <- get(id)
      result <- repository.delete(id)
    } yield result

  override def totalCount(): ZIO[WalletAccessContext, VerificationPolicyError, Long] =
    repository.totalCount()

  override def filteredCount(
      name: Option[String]
  ): ZIO[WalletAccessContext, VerificationPolicyError, Long] =
    repository.filteredCount(name)

  override def lookup(
      name: Option[String],
      offset: Option[Int],
      limit: Option[Int]
  ): ZIO[WalletAccessContext, VerificationPolicyError, List[VerificationPolicy]] =
    repository
      .lookup(name, offset, limit)

}
