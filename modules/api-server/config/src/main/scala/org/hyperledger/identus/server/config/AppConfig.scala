package org.hyperledger.identus.server.config

import org.hyperledger.identus.iam.authentication.AuthenticationConfig
import org.hyperledger.identus.shared.db.DbConfig
import org.hyperledger.identus.shared.messaging.MessagingServiceConfig
import org.hyperledger.identus.shared.models.HexString
import zio.config.magnolia.*
import zio.Config

import java.net.URL
import java.time.Duration
import scala.util.Try

enum DIDNodeBackend {
  case `prism-node`, neoprism
}

final case class AppConfig(
    credentials: CredentialsConfig,
    agent: AgentConfig,
    connections: ConnectionsConfig,
    didNode: DIDNodeConfig,
    featureFlag: FeatureFlagConfig
) {
  def validate: Either[String, Unit] =
    for {
      _ <- agent.validate
      _ <- didNode.validate
    } yield ()
}

object AppConfig {
  given Config[URL] = Config.string.mapOrFail(url =>
    val urlRegex = """^(http|https)://[a-zA-Z0-9-]+(\.[a-zA-Z0-9-]+)*(:[0-9]{1,5})?(/.*)?$""".r
    urlRegex.findFirstMatchIn(url) match
      case Some(_) =>
        Try(java.net.URI(url).toURL()).toEither.left.map(ex /*java.net.MalformedURLException*/ =>
          Config.Error.InvalidData(zio.Chunk.empty, ex.getMessage())
        )
      case _ => Left(Config.Error.InvalidData(zio.Chunk.empty, s"Invalid URL: $url"))
  )

  val config: Config[AppConfig] = deriveConfig[AppConfig]

}

final case class VaultConfig(
    address: String,
    token: Option[String],
    appRoleRoleId: Option[String],
    appRoleSecretId: Option[String],
    useSemanticPath: Boolean
) {
  def validate: Either[String, ValidatedVaultConfig] =
    val tokenConfig = token.map(ValidatedVaultConfig.TokenAuth(address, _))
    val appRoleConfig =
      for {
        roleId <- appRoleRoleId
        secretId <- appRoleSecretId
      } yield ValidatedVaultConfig.AppRoleAuth(address, roleId, secretId)

    tokenConfig
      .orElse(appRoleConfig)
      .toRight("Vault configuration is invalid. Vault token or AppRole authentication must be provided.")
}

sealed trait ValidatedVaultConfig

object ValidatedVaultConfig {
  final case class TokenAuth(address: String, token: String) extends ValidatedVaultConfig
  final case class AppRoleAuth(address: String, roleId: String, secretId: String) extends ValidatedVaultConfig
}

final case class CredentialsConfig(
    database: DatabaseConfig,
    credentialSdJwtExpirationTime: Duration,
    statusListRegistry: StatusListRegistryConfig,
    statusListSyncTriggerRecurrenceDelay: Duration,
    didStateSyncTriggerRecurrenceDelay: Duration,
    presentationInvitationExpiry: Duration,
    issuanceInvitationExpiry: Duration,
    defaultJwtVCOfferDomain: String
)
final case class ConnectionsConfig(
    database: DatabaseConfig,
    connectionsInvitationExpiry: Duration,
)

final case class GrpcServiceConfig(host: String, port: Int, usePlainText: Boolean)

final case class NeoPrismServiceConfig(baseUrl: URL)

final case class DIDNodeConfig(
    backend: DIDNodeBackend,
    prismNode: GrpcServiceConfig,
    neoprism: NeoPrismServiceConfig
) {
  def validate: Either[String, Unit] = Right(())
}

final case class StatusListRegistryConfig(publicEndpointUrl: java.net.URL)

final case class DatabaseConfig(
    host: String,
    port: Int,
    databaseName: String,
    username: String,
    password: String,
    appUsername: String,
    appPassword: String,
    awaitConnectionThreads: Int
) {
  def dbConfig(appUser: Boolean): DbConfig = {
    DbConfig(
      username = if (appUser) appUsername else username,
      password = if (appUser) appPassword else password,
      jdbcUrl = s"jdbc:postgresql://$host:$port/${databaseName}",
      awaitConnectionThreads = awaitConnectionThreads
    )
  }
}

final case class PresentationVerificationConfig(
    verifySignature: Boolean,
    verifyDates: Boolean,
    verifyHoldersBinding: Boolean,
    leeway: Duration,
)

final case class CredentialVerificationConfig(
    verifySignature: Boolean,
    verifyDates: Boolean,
    leeway: Duration,
)

final case class Options(credential: CredentialVerificationConfig, presentation: PresentationVerificationConfig)

final case class VerificationConfig(options: Options)

final case class WebhookPublisherConfig(
    url: Option[URL],
    apiKey: Option[String],
    parallelism: Option[Int]
)

final case class DefaultWalletConfig(
    enabled: Boolean,
    seed: Option[String],
    webhookUrl: Option[URL],
    webhookApiKey: Option[String],
    authApiKey: String
)

final case class AgentConfig(
    httpEndpoint: HttpEndpointConfig,
    didCommEndpoint: DidCommEndpointConfig,
    httpClient: HttpClientConfig,
    authentication: AuthenticationConfig,
    database: DatabaseConfig,
    verification: VerificationConfig,
    secretStorage: SecretStorageConfig,
    webhookPublisher: WebhookPublisherConfig,
    defaultWallet: DefaultWalletConfig,
    messagingService: MessagingServiceConfig,
    vdr: VdrConfig
) {
  def validate: Either[String, Unit] =
    for {
      _ <- Either.cond(
        defaultWallet.enabled || authentication.isEnabledAny,
        (),
        "The default wallet must be enabled if all the authentication methods are disabled. Default wallet is required for the single-tenant mode."
      )
      _ <- secretStorage.validate
      _ <- vdr.validate
    } yield ()

}

final case class HttpEndpointConfig(http: HttpConfig, serviceName: String, publicEndpointUrl: java.net.URL)

final case class DidCommEndpointConfig(http: HttpConfig, publicEndpointUrl: java.net.URL)

final case class HttpConfig(port: Int)

final case class HttpClientConfig(connectionPoolSize: Int, idleTimeout: Duration, connectionTimeout: Duration)

final case class SecretStorageConfig(
    backend: SecretStorageBackend,
    vault: Option[VaultConfig],
) {
  def validate: Either[String, Unit] =
    backend match {
      case SecretStorageBackend.vault =>
        vault
          .toRight("SecretStorage backend is set to 'vault', but vault config is not provided.")
          .flatMap(_.validate)
          .map(_ => ())
      case _ => Right(())
    }
}

enum SecretStorageBackend {
  case vault, postgres
}

final case class VdrConfig(
    inMemoryDriverEnabled: Boolean,
    databaseDriverEnabled: Boolean,
    prismDriverEnabled: Boolean,
    prismDriver: Option[PrismDriverVdrConfig],
    prismNodeDriverEnabled: Boolean,
    prismNodeDriver: Option[GrpcServiceConfig],
    defaultVdrKeyId: String = "vdr-1",
    maxDidScan: Int = 200
) {
  def validate: Either[String, Unit] =
    if prismDriverEnabled && prismDriver.isEmpty then
      Left("PRISM vdr is enabled but prismDriver config is not provided.")
    else if prismNodeDriverEnabled && prismNodeDriver.isEmpty then
      Left("Prism-node VDR is enabled but prismNodeDriver config is not provided.")
    else if maxDidScan <= 0 then Left("VDR configuration is invalid. maxDidScan must be > 0.")
    else if prismDriverEnabled then prismDriver.map(_.validate).getOrElse(Right(()))
    else Right(())
}

final case class BlockfrostPrivateNetworkConfig(
    url: String,
    protocolMagic: Int
)

final case class PrismDriverVdrConfig(
    blockfrostApiKey: Option[String],
    privateNetwork: Option[BlockfrostPrivateNetworkConfig],
    walletMnemonic: String,
    didPrism: String,
    vdrPrivateKey: String,
    stateDir: String,
    indexIntervalSecond: Int
) {
  def vdrPrivateKeyBytes: Array[Byte] = HexString.fromStringUnsafe(vdrPrivateKey).toByteArray
  def walletMnemonicSeq: Seq[String] = walletMnemonic.split(" ").toSeq.filter(_.nonEmpty)

  def validate: Either[String, Unit] =
    (blockfrostApiKey, privateNetwork) match {
      case (Some(_), Some(_)) =>
        Left("PRISM driver configuration is invalid. blockfrostApiKey and privateNetwork are mutually exclusive.")
      case (None, None) =>
        Left("PRISM driver configuration is invalid. Either blockfrostApiKey or privateNetwork must be provided.")
      case _ =>
        Right(())
    }
}
