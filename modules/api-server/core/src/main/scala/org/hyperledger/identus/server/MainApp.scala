package org.hyperledger.identus.server

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import org.hyperledger.identus.connections.controller.ConnectionControllerImpl
import org.hyperledger.identus.connections.core.service.{ConnectionServiceImpl, ConnectionServiceNotifier}
import org.hyperledger.identus.connections.sql.repository.{JdbcConnectionRepository, Migrations as ConnectMigrations}
import org.hyperledger.identus.credentials.anoncreds.AnoncredServiceLive
import org.hyperledger.identus.credentials.core.service.*
import org.hyperledger.identus.credentials.core.service.verification.VcVerificationServiceImpl
import org.hyperledger.identus.credentials.credentialdefinition.controller.CredentialDefinitionControllerImpl
import org.hyperledger.identus.credentials.credentialschema.controller.{
  CredentialSchemaControllerImpl,
  VerificationPolicyControllerImpl
}
import org.hyperledger.identus.credentials.prex.controller.PresentationExchangeControllerImpl
import org.hyperledger.identus.credentials.prex.PresentationDefinitionValidatorImpl
import org.hyperledger.identus.credentials.sdjwt.SDJwtServiceLive
import org.hyperledger.identus.credentials.sql.repository.{
  JdbcCredentialDefinitionRepository,
  JdbcCredentialRepository,
  JdbcCredentialSchemaRepository,
  JdbcCredentialStatusListRepository,
  JdbcOID4VCIIssuerMetadataRepository,
  JdbcPresentationExchangeRepository,
  JdbcPresentationRepository,
  JdbcVerificationPolicyRepository,
  Migrations as PolluxMigrations
}
import org.hyperledger.identus.credentialstatus.controller.CredentialStatusControllerImpl
import org.hyperledger.identus.did.controller.{DIDControllerImpl, DIDRegistrarControllerImpl}
import org.hyperledger.identus.did.core.util.DIDOperationValidator
import org.hyperledger.identus.didcomm.*
import org.hyperledger.identus.didcomm.controller.{DIDCommControllerConfig, DIDCommControllerImpl}
import org.hyperledger.identus.iam.authentication.{DefaultAuthenticator, Oid4vciAuthenticatorFactory}
import org.hyperledger.identus.iam.authentication.apikey.JdbcAuthenticationRepository
import org.hyperledger.identus.iam.authorization.core.EntityPermissionManagementService
import org.hyperledger.identus.iam.authorization.DefaultPermissionManagementService
import org.hyperledger.identus.iam.entity.http.controller.EntityControllerImpl
import org.hyperledger.identus.iam.wallet.http.controller.WalletManagementControllerImpl
import org.hyperledger.identus.issue.controller.{IssueControllerConfig, IssueControllerImpl}
import org.hyperledger.identus.notifications.controller.EventControllerImpl
import org.hyperledger.identus.notifications.EventNotificationServiceImpl
import org.hyperledger.identus.oid4vci.controller.{CredentialIssuerControllerConfig, CredentialIssuerControllerImpl}
import org.hyperledger.identus.oid4vci.service.OIDCCredentialIssuerServiceImpl
import org.hyperledger.identus.oid4vci.storage.InMemoryIssuanceSessionService
import org.hyperledger.identus.presentproof.controller.{PresentProofControllerConfig, PresentProofControllerImpl}
import org.hyperledger.identus.resolvers.DIDResolver
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.server.http.ZioHttpClient
import org.hyperledger.identus.server.sql.Migrations as AgentMigrations
import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.WalletIdAndRecordId
import org.hyperledger.identus.shared.models.WalletId
import org.hyperledger.identus.system.controller.SystemControllerImpl
import org.hyperledger.identus.vdr.controller.VdrControllerImpl
import org.hyperledger.identus.verification.controller.VcVerificationControllerImpl
import org.hyperledger.identus.wallet.service.{
  EntityServiceImpl,
  ManagedDIDServiceWithEventNotificationImpl,
  WalletManagementServiceImpl
}
import org.hyperledger.identus.wallet.sql.{JdbcDIDNonSecretStorage, JdbcEntityRepository, JdbcWalletNonSecretStorage}
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.logging.LogFormat.*
import zio.metrics.connectors.micrometer
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.metrics.jvm.DefaultJvmMetrics

import java.security.Security
import java.util.UUID

object MainApp extends ZIOAppDefault {

  val colorFormat: LogFormat =
    fiberId.color(LogColor.YELLOW) |-|
      line.highlight |-|
      allAnnotations |-|
      cause.highlight

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j(colorFormat)

  Security.insertProviderAt(BouncyCastleProviderSingleton.getInstance(), 2)

  // FIXME: remove this when db app user have correct privileges provisioned by k8s operator.
  // This should be executed before migration to have correct privilege for new objects.
  lazy private val preMigrations = for {
    _ <- ZIO.logDebug("Running SQL pre-migration steps.")
    appConfig <- ZIO.service[AppConfig].provide(SystemModule.configLayer)
    _ <- PolluxMigrations
      .initDbPrivileges(appConfig.credentials.database.appUsername)
      .provide(RepoModule.credentialsTransactorLayer)
    _ <- ConnectMigrations
      .initDbPrivileges(appConfig.connections.database.appUsername)
      .provide(RepoModule.connectionsTransactorLayer)
    _ <- AgentMigrations
      .initDbPrivileges(appConfig.agent.database.appUsername)
      .provide(RepoModule.agentTransactorLayer)
  } yield ()

  lazy private val migrations = for {
    _ <- ZIO.serviceWithZIO[PolluxMigrations](_.migrateAndRepair)
    _ <- ZIO.serviceWithZIO[ConnectMigrations](_.migrateAndRepair)
    _ <- ZIO.serviceWithZIO[AgentMigrations](_.migrateAndRepair)
    _ <- ZIO.logDebug("Running SQL post-migration RLS checks for DB application users")
    _ <- PolluxMigrations.validateRLS.provide(RepoModule.credentialsContextAwareTransactorLayer)
    _ <- ConnectMigrations.validateRLS.provide(RepoModule.connectionsContextAwareTransactorLayer)
    _ <- AgentMigrations.validateRLS.provide(RepoModule.agentContextAwareTransactorLayer)
  } yield ()
  override def run: ZIO[Any, Throwable, Unit] = {

    val app = for {
      appConfig <- ZIO.service[AppConfig].provide(SystemModule.configLayer)
      flags = appConfig.featureFlag
      _ <- Console.printLine(s"""
           |██████████████████████████████████████████████████████████████████
           |Starting Identus Cloud-Agent version: ${buildinfo.BuildInfo.version}
           |
           |HTTP server endpoint is setup as '${appConfig.agent.httpEndpoint.publicEndpointUrl}'
           |DIDComm server endpoint is setup as '${appConfig.agent.didCommEndpoint.publicEndpointUrl}'
           |DID Node Backend is setup as '${appConfig.didNode.backend}'
           |
           |Feature Flags:
           | - Support for the credential type JWT VC is ${if (flags.enableJWT) "ENABLED" else "DISABLED"}
           | - Support for the credential type SD JWT VC is ${if (flags.enableSDJWT) "ENABLED" else "DISABLED"}
           | - Support for the credential type AnonCreds is ${if (flags.enableAnoncred) "ENABLED" else "DISABLED"}
           |██████████████████████████████████████████████████████████████████
           |""".stripMargin)
      _ <- preMigrations
      _ <- migrations
      app <- CloudAgentApp.run
        .provide(
          DidCommX.liveLayer,
          // infra
          SystemModule.configLayer,
          ZioHttpClient.layer,
          // observability
          DefaultJvmMetrics.live.unit,
          SystemControllerImpl.layer(buildinfo.BuildInfo.version),
          ZLayer.succeed(PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
          ZLayer.succeed(MicrometerConfig.default),
          micrometer.micrometerLayer,
          // controller
          ZLayer.fromFunction((cfg: org.hyperledger.identus.server.config.AppConfig) =>
            cfg.agent.didCommEndpoint.publicEndpointUrl
          ),
          ConnectionControllerImpl.layer,
          CredentialSchemaControllerImpl.layer,
          CredentialDefinitionControllerImpl.layer,
          DIDControllerImpl.layer,
          DIDRegistrarControllerImpl.layer,
          ZLayer.fromFunction((cfg: org.hyperledger.identus.server.config.AppConfig) =>
            IssueControllerConfig(
              defaultJwtVCOfferDomain = cfg.credentials.defaultJwtVCOfferDomain,
              httpEndpointServiceName = cfg.agent.httpEndpoint.serviceName,
              httpEndpointPublicUrl = cfg.agent.httpEndpoint.publicEndpointUrl,
              issuanceInvitationExpiry = cfg.credentials.issuanceInvitationExpiry,
              didCommEndpointUrl = cfg.agent.didCommEndpoint.publicEndpointUrl,
              featureFlag = cfg.featureFlag,
            )
          ),
          IssueControllerImpl.layer,
          CredentialStatusControllerImpl.layer,
          ZLayer.fromFunction((cfg: org.hyperledger.identus.server.config.AppConfig) =>
            PresentProofControllerConfig(
              didCommEndpointUrl = cfg.agent.didCommEndpoint.publicEndpointUrl,
              presentationInvitationExpiry = cfg.credentials.presentationInvitationExpiry,
              featureFlag = cfg.featureFlag,
            )
          ),
          PresentProofControllerImpl.layer,
          VcVerificationControllerImpl.layer,
          VerificationPolicyControllerImpl.layer,
          EntityControllerImpl.layer,
          WalletManagementControllerImpl.layer,
          EventControllerImpl.layer,
          ZLayer.fromFunction((cfg: org.hyperledger.identus.server.config.AppConfig) =>
            DIDCommControllerConfig(cfg.connections.connectionsInvitationExpiry)
          ),
          DIDCommControllerImpl.layer,
          PresentationExchangeControllerImpl.layer,
          VdrControllerImpl.layer,
          // domain
          AppModule.apolloLayer,
          AppModule.didJwtResolverLayer,
          DIDOperationValidator.layer(),
          DIDResolver.layer,
          GenericUriResolverImpl.layer,
          PresentationDefinitionValidatorImpl.layer,
          // service
          AppModule.didServiceLayer,
          AppModule.vdrServiceLayer,
          ConnectionServiceImpl.layer >>> ConnectionServiceNotifier.layer,
          CredentialSchemaServiceImpl.layer,
          CredentialDefinitionServiceImpl.layer,
          CredentialStatusListServiceImpl.layer,
          SDJwtServiceLive.layer,
          AnoncredServiceLive.layer,
          LinkSecretServiceImpl.layer >>> CredentialServiceImpl.layer >>> CredentialServiceNotifier.layer,
          EntityServiceImpl.layer,
          ManagedDIDServiceWithEventNotificationImpl.layer,
          LinkSecretServiceImpl.layer >>> PresentationServiceImpl.layer >>> PresentationServiceNotifier.layer,
          VerificationPolicyServiceImpl.layer,
          WalletManagementServiceImpl.layer,
          VcVerificationServiceImpl.layer,
          PresentationExchangeServiceImpl.layer,
          // authentication
          AppModule.builtInAuthenticatorLayer,
          AppModule.keycloakAuthenticatorLayer,
          AppModule.keycloakPermissionManagementLayer,
          DefaultAuthenticator.layer,
          DefaultPermissionManagementService.layer,
          EntityPermissionManagementService.layer,
          Oid4vciAuthenticatorFactory.layer,
          // storage
          RepoModule.agentContextAwareTransactorLayer ++ RepoModule.agentTransactorLayer >>> JdbcDIDNonSecretStorage.layer,
          RepoModule.agentContextAwareTransactorLayer >>> JdbcWalletNonSecretStorage.layer,
          RepoModule.allSecretStorageLayer,
          RepoModule.agentTransactorLayer >>> JdbcEntityRepository.layer,
          RepoModule.agentTransactorLayer >>> JdbcAuthenticationRepository.layer,
          RepoModule.connectionsContextAwareTransactorLayer ++ RepoModule.connectionsTransactorLayer >>> JdbcConnectionRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcCredentialRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcCredentialStatusListRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcCredentialSchemaRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcCredentialDefinitionRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcPresentationRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcOID4VCIIssuerMetadataRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer ++ RepoModule.credentialsTransactorLayer >>> JdbcPresentationExchangeRepository.layer,
          RepoModule.credentialsContextAwareTransactorLayer >>> JdbcVerificationPolicyRepository.layer,
          // oidc
          ZLayer.fromFunction((cfg: org.hyperledger.identus.server.config.AppConfig) =>
            CredentialIssuerControllerConfig(cfg.agent.httpEndpoint.publicEndpointUrl)
          ),
          CredentialIssuerControllerImpl.layer,
          InMemoryIssuanceSessionService.layer,
          OID4VCIIssuerMetadataServiceImpl.layer,
          OIDCCredentialIssuerServiceImpl.layer,
          // event notification service
          ZLayer.succeed(500) >>> EventNotificationServiceImpl.layer,
          // HTTP client
          SystemModule.zioHttpClientLayer,
          Scope.default,
          // Messaging Service
          ZLayer.fromZIO(ZIO.service[AppConfig].map(_.agent.messagingService)),
          messaging.MessagingService.serviceLayer,
          messaging.MessagingService.producerLayer[UUID, WalletIdAndRecordId],
          messaging.MessagingService.producerLayer[WalletId, WalletId]
        )
    } yield app

    app.provide(
      RepoModule.credentialsDbConfigLayer(appUser = false) >>> PolluxMigrations.layer,
      RepoModule.connectionsDbConfigLayer(appUser = false) >>> ConnectMigrations.layer,
      RepoModule.agentDbConfigLayer(appUser = false) >>> AgentMigrations.layer,
    )
  }

}
