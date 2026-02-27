package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.{CredentialStatusList, CredentialStatusListWithCreds, DidCommID}
import org.hyperledger.identus.pollux.core.model.error.CredentialStatusListServiceError.{
  InvalidRoleForOperation,
  StatusListNotFound,
  StatusListNotFoundForIssueCredentialRecord
}
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.Role
import org.hyperledger.identus.pollux.core.repository.CredentialStatusListRepository
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.util.UUID

class CredentialStatusListServiceImpl(
    credentialService: CredentialService,
    credentialStatusListRepository: CredentialStatusListRepository,
) extends CredentialStatusListService {

  def getCredentialStatusListIds: UIO[Seq[(WalletId, UUID)]] =
    credentialStatusListRepository.getCredentialStatusListIds

  def getCredentialStatusListWithCreds(statusListId: UUID): URIO[WalletAccessContext, CredentialStatusListWithCreds] =
    credentialStatusListRepository.getCredentialStatusListsWithCreds(statusListId)

  def getById(id: UUID): IO[StatusListNotFound, CredentialStatusList] =
    for {
      maybeStatusList <- credentialStatusListRepository.findById(id)
      statusList <- ZIO
        .fromOption(maybeStatusList)
        .mapError(_ => StatusListNotFound(id))
    } yield statusList

  def revokeByIssueCredentialRecordId(
      id: DidCommID
  ): ZIO[WalletAccessContext, StatusListNotFoundForIssueCredentialRecord | InvalidRoleForOperation, Unit] =
    for {
      record <- credentialService.getById(id).orDieAsUnmanagedFailure
      _ <- if (record.role == Role.Issuer) ZIO.unit else ZIO.fail(InvalidRoleForOperation(record.role))
      exists <- credentialStatusListRepository.existsForIssueCredentialRecordId(id)
      _ <- if (exists) ZIO.unit else ZIO.fail(StatusListNotFoundForIssueCredentialRecord(id))
      _ <- credentialStatusListRepository.revokeByIssueCredentialRecordId(id)
    } yield ()

  def updateStatusListCredential(
      id: UUID,
      statusListCredential: String
  ): URIO[WalletAccessContext, Unit] =
    credentialStatusListRepository.updateStatusListCredential(id, statusListCredential)

  def markAsProcessedMany(
      credsInStatusListIds: Seq[UUID]
  ): URIO[WalletAccessContext, Unit] =
    credentialStatusListRepository.markAsProcessedMany(credsInStatusListIds)

}

object CredentialStatusListServiceImpl {
  val layer: URLayer[CredentialService & CredentialStatusListRepository, CredentialStatusListService] =
    ZLayer.fromFunction(CredentialStatusListServiceImpl(_, _))
}
