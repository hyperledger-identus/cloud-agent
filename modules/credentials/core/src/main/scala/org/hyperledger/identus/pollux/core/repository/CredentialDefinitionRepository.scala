package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.Repository.SearchCapability
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{UIO, URIO}

import java.util.UUID

trait CredentialDefinitionRepository
    extends Repository[WalletTask, CredentialDefinition]
    with SearchCapability[WalletTask, CredentialDefinition.Filter, CredentialDefinition] {
  def create(cs: CredentialDefinition): URIO[WalletAccessContext, CredentialDefinition]

  def findByGuid(guid: UUID, resolutionMethod: ResourceResolutionMethod): UIO[Option[CredentialDefinition]]

  def update(cs: CredentialDefinition): URIO[WalletAccessContext, CredentialDefinition]

  def getAllVersions(id: UUID, author: String): URIO[WalletAccessContext, Seq[String]]

  def delete(guid: UUID): URIO[WalletAccessContext, CredentialDefinition]

  def deleteAll(): URIO[WalletAccessContext, Unit]
}
