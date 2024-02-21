package io.iohk.atala.agent.server.config

import io.iohk.atala.castor.core.model.did.VerificationRelationship
import io.iohk.atala.iam.authentication.AuthenticationConfig
import io.iohk.atala.pollux.vc.jwt.*
import io.iohk.atala.shared.db.DbConfig
import zio.config.*
import zio.config.magnolia.Descriptor

import java.net.URL
import java.time.Duration
import scala.util.Try

final case class AppConfig(
    pollux: PolluxConfig,
    agent: AgentConfig,
    connect: ConnectConfig,
    prismNode: PrismNodeConfig,
) {
  def validate: Either[String, Unit] =
    for {
      _ <- agent.validate
    } yield ()
}

object AppConfig {
  val descriptor: ConfigDescriptor[AppConfig] = Descriptor[AppConfig]
}

final case class VaultConfig(
    address: String,
    token: Option[String],
    appRoleRoleId: Option[String],
    appRoleSecretId: Option[String]
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

final case class PolluxConfig(
    database: DatabaseConfig,
    issueBgJobRecordsLimit: Int,
    issueBgJobRecurrenceDelay: Duration,
    issueBgJobProcessingParallelism: Int,
    presentationBgJobRecordsLimit: Int,
    presentationBgJobRecurrenceDelay: Duration,
    presentationBgJobProcessingParallelism: Int,
)
final case class ConnectConfig(
    database: DatabaseConfig,
    connectBgJobRecordsLimit: Int,
    connectBgJobRecurrenceDelay: Duration,
    connectBgJobProcessingParallelism: Int,
    connectInvitationExpiry: Duration,
)

final case class PrismNodeConfig(service: GrpcServiceConfig)

final case class GrpcServiceConfig(host: String, port: Int, usePlainText: Boolean)

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
      jdbcUrl = s"jdbc:postgresql://${host}:${port}/${databaseName}",
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

final case class VerificationConfig(options: Options) {
  def toPresentationVerificationOptions(): JwtPresentation.PresentationVerificationOptions = {
    JwtPresentation.PresentationVerificationOptions(
      maybeProofPurpose = Some(VerificationRelationship.Authentication),
      verifySignature = options.presentation.verifySignature,
      verifyDates = options.presentation.verifyDates,
      verifyHoldersBinding = options.presentation.verifyHoldersBinding,
      leeway = options.presentation.leeway,
      maybeCredentialOptions = Some(
        CredentialVerification.CredentialVerificationOptions(
          verifySignature = options.credential.verifySignature,
          verifyDates = options.credential.verifyDates,
          leeway = options.credential.leeway,
          maybeProofPurpose = Some(VerificationRelationship.AssertionMethod)
        )
      )
    )
  }
}

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
    defaultWallet: DefaultWalletConfig
) {
  def validate: Either[String, Unit] =
    for {
      _ <- Either.cond(
        defaultWallet.enabled || authentication.isEnabledAny,
        (),
        "The default wallet must be enabled if all the authentication methods are disabled. Default wallet is required for the single-tenant mode."
      )
      _ <- secretStorage.validate
    } yield ()

}

final case class HttpEndpointConfig(http: HttpConfig, publicEndpointUrl: String)

final case class DidCommEndpointConfig(http: HttpConfig, publicEndpointUrl: String)

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
  case vault, postgres, memory
}

object SecretStorageBackend {
  given Descriptor[SecretStorageBackend] =
    Descriptor.from(
      Descriptor[String].transformOrFailLeft { s =>
        Try(SecretStorageBackend.valueOf(s)).toOption
          .toRight(
            s"Invalid configuration value '$s'. Possible values: ${SecretStorageBackend.values.mkString("[", ", ", "]")}"
          )
      }(_.toString())
    )
}
