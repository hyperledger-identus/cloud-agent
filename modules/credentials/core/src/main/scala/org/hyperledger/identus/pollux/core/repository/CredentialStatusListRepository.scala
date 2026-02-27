package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.vc.jwt.revocation.{BitString, VCStatusList2021}
import org.hyperledger.identus.pollux.vc.jwt.revocation.BitStringError.{
  DecodingError,
  EncodingError,
  IndexOutOfBounds,
  InvalidSize
}
import org.hyperledger.identus.pollux.vc.jwt.Issuer
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.json.EncoderOps

import java.util.UUID

trait CredentialStatusListRepository {
  def createStatusListVC(
      jwtIssuer: Issuer,
      statusListRegistryUrl: String,
      id: UUID
  ): IO[Throwable, String] = {
    for {
      bitString <- BitString.getInstance().mapError {
        case InvalidSize(message)      => new Throwable(message)
        case EncodingError(message)    => new Throwable(message)
        case DecodingError(message)    => new Throwable(message)
        case IndexOutOfBounds(message) => new Throwable(message)
      }
      emptyStatusListCredential <- VCStatusList2021
        .build(
          vcId = s"$statusListRegistryUrl/credential-status/$id",
          revocationData = bitString,
          jwtIssuer = jwtIssuer
        )
        .mapError(x => new Throwable(x.msg))

      credentialWithEmbeddedProof <- emptyStatusListCredential.toJsonWithEmbeddedProof
    } yield credentialWithEmbeddedProof.toJson
  }

  def getCredentialStatusListIds: UIO[Seq[(WalletId, UUID)]]

  def getCredentialStatusListsWithCreds(statusListId: UUID): URIO[WalletAccessContext, CredentialStatusListWithCreds]

  def findById(
      id: UUID
  ): UIO[Option[CredentialStatusList]]

  def incrementAndGetStatusListIndex(
      jwtIssuer: Issuer,
      statusListRegistryUrl: String
  ): URIO[WalletAccessContext, (UUID, Int)]

  def existsForIssueCredentialRecordId(
      id: DidCommID
  ): URIO[WalletAccessContext, Boolean]

  def allocateSpaceForCredential(
      issueCredentialRecordId: DidCommID,
      credentialStatusListId: UUID,
      statusListIndex: Int
  ): URIO[WalletAccessContext, Unit]

  def revokeByIssueCredentialRecordId(
      issueCredentialRecordId: DidCommID
  ): URIO[WalletAccessContext, Unit]

  def updateStatusListCredential(
      credentialStatusListId: UUID,
      statusListCredential: String
  ): URIO[WalletAccessContext, Unit]

  def markAsProcessedMany(
      credsInStatusListIds: Seq[UUID]
  ): URIO[WalletAccessContext, Unit]
}
