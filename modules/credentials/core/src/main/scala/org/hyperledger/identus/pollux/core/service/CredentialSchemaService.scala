package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaServiceError
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.*
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{IO, ZIO}

import java.util.UUID
trait CredentialSchemaService {
  private[service] type Result[T] = ZIO[WalletAccessContext, CredentialSchemaServiceError, T]

  /** @param in
    *   CredentialSchema form for creating the instance
    * @return
    *   Created instance of the Credential Schema
    */
  def create(
      in: Input,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialSchema]

  /** @param guid
    *   Globally unique UUID of the credential schema
    * @return
    *   The instance of the credential schema or credential service error
    */
  def getByGUID(
      guid: UUID,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): IO[CredentialSchemaServiceError, CredentialSchema]

  def update(
      id: UUID,
      in: Input,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialSchema]

  def lookup(filter: Filter, skip: Int, limit: Int): Result[FilteredEntries]
}
