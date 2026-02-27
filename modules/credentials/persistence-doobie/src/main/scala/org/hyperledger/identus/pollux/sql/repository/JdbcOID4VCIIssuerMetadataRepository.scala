package org.hyperledger.identus.pollux.sql.repository

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import org.hyperledger.identus.pollux.core.model.oid4vci.{CredentialConfiguration, CredentialIssuer}
import org.hyperledger.identus.pollux.core.repository.OID4VCIIssuerMetadataRepository
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.interop.catz.*

import java.net.URL
import java.time.Instant
import java.util.UUID

class JdbcOID4VCIIssuerMetadataRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task])
    extends OID4VCIIssuerMetadataRepository {

  override def findIssuerById(issuerId: UUID): UIO[Option[CredentialIssuer]] = {
    val cxnIO = sql"""
      |SELECT
      |  id,
      |  authorization_server,
      |  authorization_server_client_id,
      |  authorization_server_client_secret,
      |  created_at,
      |  updated_at
      |FROM public.issuer_metadata
      |WHERE id = $issuerId
      """.stripMargin
      .query[CredentialIssuer]
      .option

    cxnIO
      .transact(xb)
      .orDie
  }

  override def findWalletIssuers: URIO[WalletAccessContext, Seq[CredentialIssuer]] = {
    val cxnIO = sql"""
      |SELECT
      |  id,
      |  authorization_server,
      |  authorization_server_client_id,
      |  authorization_server_client_secret,
      |  created_at,
      |  updated_at
      |FROM public.issuer_metadata
      """.stripMargin
      .query[CredentialIssuer]
      .to[Seq]

    cxnIO
      .transactWallet(xa)
      .orDie
  }

  override def createIssuer(issuer: CredentialIssuer): URIO[WalletAccessContext, Unit] = {
    val cxnIO = sql"""
        |INSERT INTO public.issuer_metadata (
        |  id,
        |  authorization_server,
        |  authorization_server_client_id,
        |  authorization_server_client_secret,
        |  created_at,
        |  updated_at,
        |  wallet_id
        |) VALUES (
        |  ${issuer.id},
        |  ${issuer.authorizationServer},
        |  ${issuer.authorizationServerClientId},
        |  ${issuer.authorizationServerClientSecret},
        |  ${issuer.createdAt},
        |  ${issuer.updatedAt},
        |  current_setting('app.current_wallet_id')::UUID
        |)
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

  override def updateIssuer(
      issuerId: UUID,
      authorizationServer: Option[URL],
      authorizationServerClientId: Option[String],
      authorizationServerClientSecret: Option[String]
  ): URIO[WalletAccessContext, Unit] = {
    val setFr = (now: Instant) =>
      Fragments.set(
        fr"updated_at = $now",
        (Seq(
          authorizationServer.map(url => fr"authorization_server = $url"),
          authorizationServerClientId.map(i => fr"authorization_server_client_id = $i"),
          authorizationServerClientSecret.map(i => fr"authorization_server_client_secret = $i")
        ).flatten)*
      )
    val cxnIO = (setFr: Fragment) => sql"""
        |UPDATE public.issuer_metadata
        |$setFr
        |WHERE id = $issuerId
        """.stripMargin.update

    for {
      now <- ZIO.clockWith(_.instant)
      _ <- cxnIO(setFr(now)).run
        .transactWallet(xa)
        .ensureOneAffectedRowOrDie
    } yield ()
  }

  override def deleteIssuer(issuerId: UUID): URIO[WalletAccessContext, Unit] = {
    val cxnIO = sql"""
        | DELETE FROM public.issuer_metadata
        | WHERE id = $issuerId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

  override def createCredentialConfiguration(
      issuerId: UUID,
      config: CredentialConfiguration
  ): URIO[WalletAccessContext, Unit] = {
    val cxnIO = sql"""
        |INSERT INTO public.issuer_credential_configuration (
        |  configuration_id,
        |  issuer_id,
        |  format,
        |  schema_id,
        |  created_at
        |) VALUES (
        |  ${config.configurationId},
        |  ${issuerId},
        |  ${config.format},
        |  ${config.schemaId},
        |  ${config.createdAt}
        |)
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

  override def findCredentialConfigurationsByIssuer(issuerId: UUID): UIO[Seq[CredentialConfiguration]] = {
    val cxnIO = sql"""
      |SELECT
      |  configuration_id,
      |  format,
      |  schema_id,
      |  created_at
      |FROM public.issuer_credential_configuration
      |WHERE issuer_id = $issuerId
      """.stripMargin
      .query[CredentialConfiguration]
      .to[Seq]

    cxnIO
      .transact(xb)
      .orDie
  }

  override def findCredentialConfigurationById(
      issuerId: UUID,
      configurationId: String
  ): URIO[WalletAccessContext, Option[CredentialConfiguration]] = {
    val cxnIO = sql"""
      |SELECT
      |  configuration_id,
      |  format,
      |  schema_id,
      |  created_at
      |FROM public.issuer_credential_configuration
      |WHERE issuer_id = $issuerId AND configuration_id = $configurationId
      """.stripMargin
      .query[CredentialConfiguration]
      .option

    cxnIO
      .transactWallet(xa)
      .orDie
  }

  override def deleteCredentialConfiguration(
      issuerId: UUID,
      configurationId: String
  ): URIO[WalletAccessContext, Unit] = {
    val cxnIO = sql"""
        | DELETE FROM public.issuer_credential_configuration
        | WHERE issuer_id = $issuerId AND configuration_id = $configurationId
        """.stripMargin.update

    cxnIO.run
      .transactWallet(xa)
      .ensureOneAffectedRowOrDie
  }

}

object JdbcOID4VCIIssuerMetadataRepository {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], OID4VCIIssuerMetadataRepository] =
    ZLayer.fromFunction(new JdbcOID4VCIIssuerMetadataRepository(_, _))
}
