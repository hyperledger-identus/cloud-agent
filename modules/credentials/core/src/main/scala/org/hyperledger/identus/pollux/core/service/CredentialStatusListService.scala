package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.{CredentialStatusList, CredentialStatusListWithCreds, DidCommID}
import org.hyperledger.identus.pollux.core.model.error.CredentialStatusListServiceError.{
  InvalidRoleForOperation,
  StatusListNotFound,
  StatusListNotFoundForIssueCredentialRecord
}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.util.UUID

trait CredentialStatusListService {
  def getById(
      id: UUID
  ): IO[StatusListNotFound, CredentialStatusList]

  def revokeByIssueCredentialRecordId(
      id: DidCommID
  ): ZIO[WalletAccessContext, StatusListNotFoundForIssueCredentialRecord | InvalidRoleForOperation, Unit]

  def getCredentialStatusListIds: UIO[Seq[(WalletId, UUID)]]

  def getCredentialStatusListWithCreds(statusListId: UUID): URIO[WalletAccessContext, CredentialStatusListWithCreds]

  def updateStatusListCredential(
      id: UUID,
      statusListCredential: String
  ): URIO[WalletAccessContext, Unit]

  def markAsProcessedMany(
      ids: Seq[UUID]
  ): URIO[WalletAccessContext, Unit]
}
