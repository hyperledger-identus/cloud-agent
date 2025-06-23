package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.agent.walletapi.storage
import org.hyperledger.identus.agent.walletapi.storage.GenericSecretStorage
import org.hyperledger.identus.pollux.anoncreds.{AnoncredLib, AnoncredSchemaDef}
import org.hyperledger.identus.pollux.core.model.error.{
  CredentialDefinitionCreationError,
  CredentialDefinitionGuidNotFoundError,
  CredentialDefinitionServiceError,
  CredentialDefinitionValidationError,
  CredentialSchemaError
}
import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError.{
  CredentialSchemaParsingError,
  CredentialSchemaValidationError
}
import org.hyperledger.identus.pollux.core.model.schema.`type`.anoncred.AnoncredSchemaSerDesV1
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition.FilteredEntries
import org.hyperledger.identus.pollux.core.model.secret.CredentialDefinitionSecret
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.CredentialDefinitionRepository
import org.hyperledger.identus.pollux.core.repository.Repository.SearchQuery
import org.hyperledger.identus.pollux.core.service.serdes.{
  PrivateCredentialDefinitionSchemaSerDesV1,
  ProofKeyCredentialDefinitionSchemaSerDesV1,
  PublicCredentialDefinitionSerDesV1
}
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.json.JsonSchemaError
import zio.*

import java.util.UUID
import scala.util.Try

class CredentialDefinitionServiceImpl(
    genericSecretStorage: GenericSecretStorage,
    credentialDefinitionRepository: CredentialDefinitionRepository,
    uriResolver: UriResolver
) extends CredentialDefinitionService {

  override def create(
      in: CredentialDefinition.Input,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ): Result[CredentialDefinition] = {
    for {
      content <- uriResolver.resolve(in.schemaId).orDieAsUnmanagedFailure
      anoncredSchema <- AnoncredSchemaSerDesV1.schemaSerDes
        .deserialize(content)
        .mapError(error => CredentialSchemaParsingError(error.error))
        .orDieAsUnmanagedFailure
      anoncredLibSchema =
        AnoncredSchemaDef(
          in.schemaId,
          anoncredSchema.version,
          anoncredSchema.attrNames,
          anoncredSchema.issuerId
        )
      anoncredLibCredentialDefinition <-
        ZIO
          .fromEither(
            Try(
              AnoncredLib.createCredDefinition(
                in.author,
                anoncredLibSchema,
                in.tag,
                in.supportRevocation
              )
            ).toEither
          )
          .mapError(t => CredentialDefinitionCreationError(t.getMessage))
      publicCredentialDefinitionJson <-
        PublicCredentialDefinitionSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.cd.data
        )
      privateCredentialDefinitionJson <-
        PrivateCredentialDefinitionSchemaSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.cdPrivate.data
        )
      proofKeyCredentialDefinitionJson <-
        ProofKeyCredentialDefinitionSchemaSerDesV1.schemaSerDes.deserializeAsJson(
          anoncredLibCredentialDefinition.proofKey.data
        )
      cd <-
        CredentialDefinition.make(
          in,
          PublicCredentialDefinitionSerDesV1.version,
          publicCredentialDefinitionJson,
          ProofKeyCredentialDefinitionSchemaSerDesV1.version,
          proofKeyCredentialDefinitionJson,
          resolutionMethod
        )
      createdCredentialDefinition <- credentialDefinitionRepository.create(cd)
      _ <- genericSecretStorage
        .set(
          createdCredentialDefinition.guid,
          CredentialDefinitionSecret(privateCredentialDefinitionJson)
        )
        .mapError(t =>
          CredentialDefinitionCreationError(s"An error occurred while storing the CredDef secret: ${t.getMessage}")
        )
    } yield createdCredentialDefinition
  }.mapError {
    case error: JsonSchemaError =>
      CredentialDefinitionValidationError(CredentialSchemaValidationError(error))
    case error: CredentialDefinitionCreationError => error
  }

  override def getByGUID(
      guid: UUID,
      resolutionMethod: ResourceResolutionMethod
  ): IO[CredentialDefinitionServiceError, CredentialDefinition] = {
    for {
      resultOpt <- credentialDefinitionRepository.findByGuid(guid, resolutionMethod)
      result <- ZIO.fromOption(resultOpt).mapError(_ => CredentialDefinitionGuidNotFoundError(guid))
    } yield result
  }

  override def lookup(filter: CredentialDefinition.Filter, skip: Int, limit: Int): Result[FilteredEntries] = {
    credentialDefinitionRepository
      .search(SearchQuery(filter, skip, limit))
      .map(sr => FilteredEntries(sr.entries, sr.count.toInt, sr.totalCount.toInt))
  }
}

object CredentialDefinitionServiceImpl {
  val layer: URLayer[
    GenericSecretStorage & CredentialDefinitionRepository & UriResolver,
    CredentialDefinitionService
  ] =
    ZLayer.fromFunction(CredentialDefinitionServiceImpl(_, _, _))
}
