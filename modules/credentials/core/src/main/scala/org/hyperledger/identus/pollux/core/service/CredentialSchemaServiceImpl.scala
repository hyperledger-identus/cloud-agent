package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.error.{
  CredentialSchemaError,
  CredentialSchemaGuidNotFoundError,
  CredentialSchemaIdNotFoundError,
  CredentialSchemaServiceError,
  CredentialSchemaUpdateError,
  CredentialSchemaValidationError
}
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema.FilteredEntries
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.CredentialSchemaRepository
import org.hyperledger.identus.pollux.core.repository.Repository.SearchQuery
import zio.*

import java.util.UUID

class CredentialSchemaServiceImpl(
    credentialSchemaRepository: CredentialSchemaRepository
) extends CredentialSchemaService {
  override def create(
      in: CredentialSchema.Input,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialSchema] = {
    for {
      credentialSchema <- CredentialSchema.make(in, resolutionMethod)
      _ <- CredentialSchema.validateCredentialSchema(credentialSchema)
      createdCredentialSchema <- credentialSchemaRepository.create(credentialSchema)
    } yield createdCredentialSchema
  }.mapError { (e: CredentialSchemaError) =>
    CredentialSchemaValidationError(e)
  }

  override def getByGUID(
      guid: UUID,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): IO[CredentialSchemaServiceError, CredentialSchema] = {
    for {
      resultOpt <- credentialSchemaRepository.findByGuid(guid, resolutionMethod)
      result <- ZIO.fromOption(resultOpt).mapError(_ => CredentialSchemaGuidNotFoundError(guid))
    } yield result
  }

  def getBy(
      author: String,
      id: UUID,
      version: String,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialSchema] = {
    getByGUID(CredentialSchema.makeGUID(author, id, version), resolutionMethod)
  }

  override def update(
      id: UUID,
      in: CredentialSchema.Input,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialSchema] = {

    for {
      existingVersions <- credentialSchemaRepository.getAllVersions(id, in.author, resolutionMethod)
      _ <-
        if existingVersions.isEmpty then
          ZIO.fail(
            CredentialSchemaUpdateError(
              id,
              in.version,
              in.author,
              s"No Schema exists of author: ${in.author}, with provided id: $id"
            )
          )
        else ZIO.unit
      resolutionMethod = existingVersions.head.resolutionMethod
      cs <- CredentialSchema.make(id, in, resolutionMethod)
      _ <- CredentialSchema.validateCredentialSchema(cs).mapError(CredentialSchemaValidationError.apply)
      _ <- ZIO.fromOption(existingVersions.headOption).mapError(_ => CredentialSchemaIdNotFoundError(id))
      _ <- existingVersions.find(_.version > in.version) match {
        case Some(higherVersion) =>
          ZIO.fail(
            CredentialSchemaUpdateError(
              id,
              in.version,
              in.author,
              s"Higher version is found: $higherVersion"
            )
          )
        case None =>
          ZIO.succeed(cs)
      }
      _ <- existingVersions.find(_.version == in.version) match {
        case Some(existingVersion) =>
          ZIO.fail(
            CredentialSchemaUpdateError(
              id,
              in.version,
              in.author,
              s"The version already exists: $existingVersion"
            )
          )
        case None => ZIO.succeed(cs)
      }
      updated <- credentialSchemaRepository.create(cs)
    } yield updated
  }

  override def lookup(
      filter: CredentialSchema.Filter,
      skip: Int,
      limit: Int,
  ): Result[CredentialSchema.FilteredEntries] = {
    credentialSchemaRepository
      .search(SearchQuery(filter, skip, limit))
      .map(sr => FilteredEntries(sr.entries, sr.count.toInt, sr.totalCount.toInt))
  }
}

object CredentialSchemaServiceImpl {
  val layer: URLayer[CredentialSchemaRepository, CredentialSchemaService] =
    ZLayer.fromFunction(CredentialSchemaServiceImpl(_))
}
