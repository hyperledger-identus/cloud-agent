package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.oid4vci.{CredentialConfiguration, CredentialIssuer}
import org.hyperledger.identus.pollux.core.model.CredentialFormat
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.mock.{Expectation, Mock, Proxy}
import zio.test.Assertion

import java.net.URL
import java.util.UUID

object MockOID4VCIIssuerMetadataService extends Mock[OID4VCIIssuerMetadataService] {

  import OID4VCIIssuerMetadataServiceError.*

  object GetCredentialConfigurationById
      extends Effect[
        (UUID, String),
        CredentialConfigurationNotFound,
        CredentialConfiguration
      ]

  override val compose: URLayer[mock.Proxy, OID4VCIIssuerMetadataService] = ZLayer {
    ZIO.serviceWith[Proxy] { proxy =>
      new OID4VCIIssuerMetadataService {
        override def getCredentialIssuer(issuerId: UUID): IO[IssuerIdNotFound, CredentialIssuer] =
          ZIO.die(NotImplementedError())

        override def createCredentialIssuer(issuer: CredentialIssuer): URIO[WalletAccessContext, CredentialIssuer] =
          ZIO.die(NotImplementedError())

        override def getCredentialIssuers: URIO[WalletAccessContext, Seq[CredentialIssuer]] =
          ZIO.die(NotImplementedError())

        override def updateCredentialIssuer(
            issuerId: UUID,
            authorizationServer: Option[URL] = None,
            authorizationServerClientId: Option[String] = None,
            authorizationServerClientSecret: Option[String] = None
        ): ZIO[WalletAccessContext, IssuerIdNotFound, CredentialIssuer] = ZIO.die(NotImplementedError())

        override def deleteCredentialIssuer(issuerId: UUID): ZIO[WalletAccessContext, IssuerIdNotFound, Unit] =
          ZIO.die(NotImplementedError())

        override def createCredentialConfiguration(
            issuerId: UUID,
            format: CredentialFormat,
            configurationId: String,
            schemaId: String
        ): ZIO[WalletAccessContext, InvalidSchemaId | UnsupportedCredentialFormat, CredentialConfiguration] =
          ZIO.die(NotImplementedError())

        override def getCredentialConfigurations(
            issuerId: UUID
        ): IO[IssuerIdNotFound, Seq[CredentialConfiguration]] = ZIO.die(NotImplementedError())

        override def getCredentialConfigurationById(
            issuerId: UUID,
            configurationId: String
        ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, CredentialConfiguration] =
          proxy(GetCredentialConfigurationById, issuerId, configurationId)

        override def deleteCredentialConfiguration(
            issuerId: UUID,
            configurationId: String,
        ): ZIO[WalletAccessContext, CredentialConfigurationNotFound, Unit] = ZIO.die(NotImplementedError())
      }
    }
  }

  def getCredentialConfigurationByIdExpectations(
      configuration: CredentialConfiguration
  ): Expectation[OID4VCIIssuerMetadataService] =
    GetCredentialConfigurationById(
      assertion = Assertion.anything,
      result = Expectation.value(configuration)
    )
}
