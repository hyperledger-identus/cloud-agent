package org.hyperledger.identus.agent.server

import org.hyperledger.identus.agent.notification.WebhookPublisher
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.http.{ZHttp4sBlazeServer, ZHttpEndpoints}
import org.hyperledger.identus.agent.server.jobs.*
import org.hyperledger.identus.agent.walletapi.model.{Entity, Wallet, WalletSeed}
import org.hyperledger.identus.agent.walletapi.service.{EntityService, WalletManagementService}
import org.hyperledger.identus.castor.controller.{DIDRegistrarServerEndpoints, DIDServerEndpoints}
import org.hyperledger.identus.connect.controller.ConnectionServerEndpoints
import org.hyperledger.identus.credentialstatus.controller.CredentialStatusServiceEndpoints
import org.hyperledger.identus.event.controller.EventServerEndpoints
import org.hyperledger.identus.event.notification.EventNotificationConfig
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyAuthenticator
import org.hyperledger.identus.iam.entity.http.EntityServerEndpoints
import org.hyperledger.identus.iam.wallet.http.WalletManagementServerEndpoints
import org.hyperledger.identus.issue.controller.IssueServerEndpoints
import org.hyperledger.identus.oid4vci.CredentialIssuerServerEndpoints
import org.hyperledger.identus.pollux.credentialdefinition.CredentialDefinitionRegistryServerEndpoints
import org.hyperledger.identus.pollux.credentialschema.{
  SchemaRegistryServerEndpoints,
  VerificationPolicyServerEndpoints
}
import org.hyperledger.identus.pollux.prex.PresentationExchangeServerEndpoints
import org.hyperledger.identus.presentproof.controller.PresentProofServerEndpoints
import org.hyperledger.identus.shared.models.*
import org.hyperledger.identus.system.controller.SystemServerEndpoints
import org.hyperledger.identus.vdr.controller.VdrServerEndpoints
import org.hyperledger.identus.verification.controller.VcVerificationServerEndpoints
import zio.*
object CloudAgentApp {

  def run = for {
    _ <- AgentInitialization.run
    _ <- ConnectBackgroundJobs.connectFlowsHandler
    _ <- IssueBackgroundJobs.issueFlowsHandler
    _ <- PresentBackgroundJobs.presentFlowsHandler
    _ <- DIDStateSyncBackgroundJobs.didStateSyncTrigger
    _ <- DIDStateSyncBackgroundJobs.didStateSyncHandler
    _ <- StatusListJobs.statusListsSyncTrigger
    _ <- StatusListJobs.statusListSyncHandler
    _ <- AgentHttpServer.run.tapDefect(e => ZIO.logErrorCause("Agent HTTP Server failure", e)).fork
    fiber <- DidCommHttpServer.run.tapDefect(e => ZIO.logErrorCause("DIDComm HTTP Server failure", e)).fork
    _ <- WebhookPublisher.layer.build.map(_.get[WebhookPublisher]).flatMap(_.run.fork)
    _ <- fiber.join *> ZIO.log(s"Server End")
    _ <- ZIO.never
  } yield ()
}

object AgentHttpServer {
  val agentRESTServiceEndpoints = for {
    allCredentialDefinitionRegistryEndpoints <- CredentialDefinitionRegistryServerEndpoints.all
    allSchemaRegistryEndpoints <- SchemaRegistryServerEndpoints.all
    allVerificationPolicyEndpoints <- VerificationPolicyServerEndpoints.all
    allConnectionEndpoints <- ConnectionServerEndpoints.all
    allIssueEndpoints <- IssueServerEndpoints.all
    allStatusListEndpoints <- CredentialStatusServiceEndpoints.all
    allDIDEndpoints <- DIDServerEndpoints.all
    allDIDRegistrarEndpoints <- DIDRegistrarServerEndpoints.all
    allPresentProofEndpoints <- PresentProofServerEndpoints.all
    allVcVerificationEndpoints <- VcVerificationServerEndpoints.all
    allSystemEndpoints <- SystemServerEndpoints.all
    allEntityEndpoints <- EntityServerEndpoints.all
    allWalletManagementEndpoints <- WalletManagementServerEndpoints.all
    allEventEndpoints <- EventServerEndpoints.all
    allOIDCEndpoints <- CredentialIssuerServerEndpoints.all
    allPresentationExchangeEndpoints <- PresentationExchangeServerEndpoints.all
    allVdrEndpoints <- VdrServerEndpoints.all
  } yield allCredentialDefinitionRegistryEndpoints ++
    allSchemaRegistryEndpoints ++
    allVerificationPolicyEndpoints ++
    allConnectionEndpoints ++
    allDIDEndpoints ++
    allDIDRegistrarEndpoints ++
    allIssueEndpoints ++
    allStatusListEndpoints ++
    allPresentProofEndpoints ++
    allVcVerificationEndpoints ++
    allPresentationExchangeEndpoints ++
    allSystemEndpoints ++
    allEntityEndpoints ++
    allWalletManagementEndpoints ++
    allEventEndpoints ++
    allOIDCEndpoints ++
    allVdrEndpoints

  def run =
    for {
      allEndpoints <- agentRESTServiceEndpoints
      allEndpointsWithDocumentation = ZHttpEndpoints.withDocumentations[Task](allEndpoints)
      server <- ZHttp4sBlazeServer.make("rest_api")
      appConfig <- ZIO.service[AppConfig]
      _ <- server.start(allEndpointsWithDocumentation, port = appConfig.agent.httpEndpoint.http.port).debug
    } yield ()
}

object AgentInitialization {

  private val defaultWalletId = WalletId.default
  private val defaultWallet = Wallet("default", defaultWalletId)
  private val defaultEntity = Entity.Default

  def run: RIO[AppConfig & WalletManagementService & EntityService & ApiKeyAuthenticator, Unit] =
    for {
      _ <- validateAppConfig
      _ <- initializeDefaultWallet
        .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))
    } yield ()

  private val validateAppConfig =
    ZIO.serviceWithZIO[AppConfig](conf =>
      ZIO
        .fromEither(conf.validate)
        .mapError(msg => RuntimeException(s"Application configuration is invalid. $msg"))
    )

  private val initializeDefaultWallet =
    for {
      _ <- ZIO.logInfo("Initializing default wallet.")
      config <- ZIO.serviceWith[AppConfig](_.agent.defaultWallet)
      walletService <- ZIO.service[WalletManagementService]
      isDefaultWalletEnabled = config.enabled
      isDefaultWalletExist <- walletService
        .findWallet(defaultWalletId)
        .map(_.isDefined)
      _ <- ZIO.logInfo(s"Default wallet not enabled.").when(!isDefaultWalletEnabled)
      _ <- ZIO.logInfo(s"Default wallet already exist.").when(isDefaultWalletExist)
      _ <- createDefaultWallet.when(isDefaultWalletEnabled && !isDefaultWalletExist)
    } yield ()

  private val createDefaultWallet =
    for {
      walletService <- ZIO.service[WalletManagementService]
      entityService <- ZIO.service[EntityService]
      apiKeyAuth <- ZIO.service[ApiKeyAuthenticator]
      config <- ZIO.serviceWith[AppConfig](_.agent.defaultWallet)
      seed <- config.seed.fold(ZIO.none) { seedHex =>
        ZIO
          .fromTry(HexString.fromString(seedHex))
          .map(bytes => WalletSeed.fromByteArray(bytes.toByteArray).left.map(Exception(_)))
          .absolve
          .asSome
      }
      _ <- ZIO.logInfo(s"Default wallet seed is not provided. New seed will be generated.").when(seed.isEmpty)
      _ <- walletService
        .createWallet(defaultWallet, seed)
        .orDieAsUnmanagedFailure
      _ <- entityService.create(defaultEntity).orDieAsUnmanagedFailure
      _ <- apiKeyAuth.add(defaultEntity.id, config.authApiKey)
      _ <- config.webhookUrl.fold(ZIO.unit) { url =>
        val customHeaders = config.webhookApiKey.fold(Map.empty)(apiKey => Map("Authorization" -> s"Bearer $apiKey"))
        walletService
          .createWalletNotification(EventNotificationConfig(defaultWalletId, url, customHeaders))
          .orDieAsUnmanagedFailure
          .provide(ZLayer.succeed(WalletAccessContext(defaultWalletId)))
      }
    } yield ()

}
