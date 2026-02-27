package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError.{
  CredentialSchemaParsingError,
  SchemaDereferencingError
}
import org.hyperledger.identus.pollux.core.model.oid4vci.{CredentialConfiguration, CredentialIssuer}
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.CredentialFormat
import org.hyperledger.identus.pollux.core.repository.OID4VCIIssuerMetadataRepository
import org.hyperledger.identus.pollux.core.service.OID4VCIIssuerMetadataServiceError.{
  CredentialConfigurationNotFound,
  DuplicateCredentialConfigId,
  InvalidSchemaId,
  IssuerIdNotFound,
  UnsupportedCredentialFormat
}
import org.hyperledger.identus.shared.db.Errors.UnexpectedAffectedRow
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.{Failure, StatusCode, WalletAccessContext}
import zio.*

import java.net.URL
import java.util.UUID

sealed trait OID4VCIIssuerMetadataServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "OID4VCIIssuerMetadataServiceError"
}

object OID4VCIIssuerMetadataServiceError {
  final case class IssuerIdNotFound(issuerId: UUID)
      extends OID4VCIIssuerMetadataServiceError(
        StatusCode.NotFound,
        s"There is no credential issuer matching the provided identifier: issuerId=$issuerId"
      )

  final case class CredentialConfigurationNotFound(issuerId: UUID, configurationId: String)
      extends OID4VCIIssuerMetadataServiceError(
        StatusCode.NotFound,
        s"There is no credential configuration matching the provided identifier: issuerId=$issuerId, configurationId=$configurationId"
      )

  final case class InvalidSchemaId(schemaId: String, msg: String)
      extends OID4VCIIssuerMetadataServiceError(
        StatusCode.BadRequest,
        s"Invalid schemaId $schemaId. $msg"
      )

  final case class DuplicateCredentialConfigId(id: String)
      extends OID4VCIIssuerMetadataServiceError(
        StatusCode.Conflict,
        s"Duplicated credential configuration id: $id"
      )

  final case class UnsupportedCredentialFormat(format: CredentialFormat)
      extends OID4VCIIssuerMetadataServiceError(
        StatusCode.BadRequest,
        s"Unsupported credential format in OID4VCI protocol: $format"
      )
}

trait OID4VCIIssuerMetadataService {
  def getCredentialIssuer(issuerId: UUID): IO[IssuerIdNotFound, CredentialIssuer]
  def createCredentialIssuer(issuer: CredentialIssuer): URIO[WalletAccessContext, CredentialIssuer]
  def getCredentialIssuers: URIO[WalletAccessContext, Seq[CredentialIssuer]]
  def updateCredentialIssuer(
      issuerId: UUID,
      authorizationServer: Option[URL] = None,
      authorizationServerClientId: Option[String] = None,
      authorizationServerClientSecret: Option[String] = None
  ): ZIO[WalletAccessContext, IssuerIdNotFound, CredentialIssuer]
  def deleteCredentialIssuer(issuerId: UUID): ZIO[WalletAccessContext, IssuerIdNotFound, Unit]
  def createCredentialConfiguration(
      issuerId: UUID,
      format: CredentialFormat,
      configurationId: String,
      schemaId: String
  ): ZIO[
    WalletAccessContext,
    InvalidSchemaId | UnsupportedCredentialFormat | IssuerIdNotFound | DuplicateCredentialConfigId,
    CredentialConfiguration
  ]
  def getCredentialConfigurations(
      issuerId: UUID
  ): IO[IssuerIdNotFound, Seq[CredentialConfiguration]]
  def getCredentialConfigurationById(
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, CredentialConfiguration]
  def deleteCredentialConfiguration(
      issuerId: UUID,
      configurationId: String,
  ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, Unit]
}

class OID4VCIIssuerMetadataServiceImpl(repository: OID4VCIIssuerMetadataRepository, uriResolver: UriResolver)
    extends OID4VCIIssuerMetadataService {

  override def createCredentialIssuer(issuer: CredentialIssuer): URIO[WalletAccessContext, CredentialIssuer] =
    repository.createIssuer(issuer).as(issuer)

  override def getCredentialIssuers: URIO[WalletAccessContext, Seq[CredentialIssuer]] =
    repository.findWalletIssuers

  override def getCredentialIssuer(issuerId: UUID): IO[IssuerIdNotFound, CredentialIssuer] =
    repository
      .findIssuerById(issuerId)
      .someOrFail(IssuerIdNotFound(issuerId))

  override def updateCredentialIssuer(
      issuerId: UUID,
      authorizationServer: Option[URL],
      authorizationServerClientId: Option[String],
      authorizationServerClientSecret: Option[String]
  ): ZIO[WalletAccessContext, IssuerIdNotFound, CredentialIssuer] =
    for {
      _ <- repository
        .updateIssuer(
          issuerId = issuerId,
          authorizationServer = authorizationServer,
          authorizationServerClientId = authorizationServerClientId,
          authorizationServerClientSecret = authorizationServerClientSecret
        )
        .catchSomeDefect { case _: UnexpectedAffectedRow =>
          ZIO.fail(IssuerIdNotFound(issuerId))
        }
      updatedIssuer <- getCredentialIssuer(issuerId)
    } yield updatedIssuer

  override def deleteCredentialIssuer(issuerId: UUID): ZIO[WalletAccessContext, IssuerIdNotFound, Unit] =
    repository
      .deleteIssuer(issuerId)
      .catchSomeDefect { case _: UnexpectedAffectedRow =>
        ZIO.fail(IssuerIdNotFound(issuerId))
      }

  override def createCredentialConfiguration(
      issuerId: UUID,
      format: CredentialFormat,
      configurationId: String,
      schemaId: String
  ): ZIO[
    WalletAccessContext,
    InvalidSchemaId | UnsupportedCredentialFormat | IssuerIdNotFound | DuplicateCredentialConfigId,
    CredentialConfiguration
  ] = {
    for {
      _ <- getCredentialIssuer(issuerId)
      _ <- getCredentialConfigurationById(issuerId, configurationId).flip
        .mapError(_ => DuplicateCredentialConfigId(configurationId))
      _ <- format match {
        case CredentialFormat.JWT => ZIO.unit
        case f                    => ZIO.fail(UnsupportedCredentialFormat(f))
      }
      schemaUri <- UriString.make(schemaId).toZIO.mapError(msg => InvalidSchemaId(schemaId, msg))
      _ <- CredentialSchema
        .validSchemaValidator(schemaUri, uriResolver)
        .catchAll {
          case e: SchemaDereferencingError     => ZIO.fail(InvalidSchemaId(schemaId, e.userFacingMessage))
          case e: CredentialSchemaParsingError => ZIO.fail(InvalidSchemaId(schemaId, e.cause))
        }
      now <- ZIO.clockWith(_.instant)
      config = CredentialConfiguration(
        configurationId = configurationId,
        format = CredentialFormat.JWT,
        schemaId = schemaUri.toUri, // TODO: stopped refactoring here
        createdAt = now
      )
      _ <- repository.createCredentialConfiguration(issuerId, config)
    } yield config
  }

  override def getCredentialConfigurations(
      issuerId: UUID
  ): IO[IssuerIdNotFound, Seq[CredentialConfiguration]] =
    repository
      .findIssuerById(issuerId)
      .someOrFail(IssuerIdNotFound(issuerId))
      .flatMap(_ => repository.findCredentialConfigurationsByIssuer(issuerId))

  override def getCredentialConfigurationById(
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, CredentialConfiguration] =
    repository
      .findCredentialConfigurationById(issuerId, configurationId)
      .someOrFail(CredentialConfigurationNotFound(issuerId, configurationId))

  override def deleteCredentialConfiguration(
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, Unit] =
    repository
      .deleteCredentialConfiguration(issuerId, configurationId)
      .catchSomeDefect { case _: UnexpectedAffectedRow =>
        ZIO.fail(CredentialConfigurationNotFound(issuerId, configurationId))
      }
}

object OID4VCIIssuerMetadataServiceImpl {
  def layer: URLayer[OID4VCIIssuerMetadataRepository & UriResolver, OID4VCIIssuerMetadataService] = {
    ZLayer.fromFunction(OID4VCIIssuerMetadataServiceImpl(_, _))
  }
}
