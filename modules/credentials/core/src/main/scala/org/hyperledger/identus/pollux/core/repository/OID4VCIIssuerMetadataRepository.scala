package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.oid4vci.{CredentialConfiguration, CredentialIssuer}
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.net.URL
import java.util.UUID

trait OID4VCIIssuerMetadataRepository {
  def findIssuerById(issuerId: UUID): UIO[Option[CredentialIssuer]]
  def createIssuer(issuer: CredentialIssuer): URIO[WalletAccessContext, Unit]
  def findWalletIssuers: URIO[WalletAccessContext, Seq[CredentialIssuer]]
  def updateIssuer(
      issuerId: UUID,
      authorizationServer: Option[URL] = None,
      authorizationServerClientId: Option[String] = None,
      authorizationServerClientSecret: Option[String] = None
  ): URIO[WalletAccessContext, Unit]
  def deleteIssuer(issuerId: UUID): URIO[WalletAccessContext, Unit]
  def createCredentialConfiguration(issuerId: UUID, config: CredentialConfiguration): URIO[WalletAccessContext, Unit]
  def findCredentialConfigurationsByIssuer(issuerId: UUID): UIO[Seq[CredentialConfiguration]]
  def findCredentialConfigurationById(
      issuerId: UUID,
      configurationId: String
  ): URIO[WalletAccessContext, Option[CredentialConfiguration]]
  def deleteCredentialConfiguration(issuerId: UUID, configurationId: String): URIO[WalletAccessContext, Unit]
}
