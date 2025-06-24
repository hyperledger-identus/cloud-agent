package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.Repository.SearchCapability
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{UIO, URIO}

import java.util.UUID

trait CredentialSchemaRepository
    extends Repository[WalletTask, CredentialSchema]
    with SearchCapability[WalletTask, CredentialSchema.Filter, CredentialSchema] {
  def create(cs: CredentialSchema): URIO[WalletAccessContext, CredentialSchema]

  def findByGuid(guid: UUID, resolutionMethod: ResourceResolutionMethod): UIO[Option[CredentialSchema]]

  def update(cs: CredentialSchema): URIO[WalletAccessContext, CredentialSchema]

  def getAllVersions(
      id: UUID,
      author: String,
      resolutionMethod: ResourceResolutionMethod
  ): URIO[WalletAccessContext, List[CredentialSchema]]

  def delete(guid: UUID): URIO[WalletAccessContext, CredentialSchema]

  def deleteAll(): URIO[WalletAccessContext, Unit]
}
