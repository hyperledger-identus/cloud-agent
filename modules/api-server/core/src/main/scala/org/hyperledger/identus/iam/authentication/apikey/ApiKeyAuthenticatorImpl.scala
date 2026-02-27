package org.hyperledger.identus.iam.authentication.apikey

import org.hyperledger.identus.agent.walletapi.model.{Entity, Wallet}
import org.hyperledger.identus.agent.walletapi.service.{EntityService, WalletManagementService}
import org.hyperledger.identus.iam.authentication.AuthenticationError
import org.hyperledger.identus.iam.authentication.AuthenticationError.*
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import zio.{IO, UIO, URLayer, ZIO, ZLayer}

import java.util.UUID
import scala.language.implicitConversions
import scala.util.Try

case class ApiKeyAuthenticatorImpl(
    apiKeyConfig: ApiKeyConfig,
    repository: AuthenticationRepository,
    entityService: EntityService,
    walletManagementService: WalletManagementService
) extends ApiKeyAuthenticator {

  override def isEnabled: Boolean = apiKeyConfig.enabled

  override def authenticate(apiKey: String): IO[InvalidCredentials, Entity] = {
    if (apiKeyConfig.enabled) {
      if (apiKeyConfig.authenticateAsDefaultUser) {
        ZIO.succeed(Entity.Default)
      } else {
        authenticateBy(apiKey)
          .catchSome {
            case InvalidCredentials(message) if apiKeyConfig.autoProvisioning =>
              provisionNewEntity(apiKey)
          }
      }
    } else {
      ZIO
        .fail(
          AuthenticationMethodNotEnabled(s"Authentication method not enabled: ${AuthenticationMethodType.ApiKey.value}")
        )
        .orDieAsUnmanagedFailure
    }
  }

  protected[apikey] def provisionNewEntity(apiKey: String): UIO[Entity] = synchronized {
    for {
      wallet <- walletManagementService
        .createWallet(Wallet("Auto provisioned wallet", WalletId.random))
        .orDieAsUnmanagedFailure
        .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      entityToCreate = Entity(name = "Auto provisioned entity", walletId = wallet.id.toUUID)
      entity <- entityService.create(entityToCreate).orDieAsUnmanagedFailure
      _ <- add(entity.id, apiKey)
    } yield entity
  }

  protected[apikey] def authenticateBy(apiKey: String): IO[InvalidCredentials, Entity] = {
    for {
      saltAndApiKey <- ZIO.succeed(apiKeyConfig.salt + apiKey)
      secret <- ZIO
        .fromTry(Try(Sha256Hash.compute(saltAndApiKey.getBytes).hexEncoded))
        .orDie
      entityId <- repository
        .findEntityIdByMethodAndSecret(AuthenticationMethodType.ApiKey, secret)
        .someOrFail(InvalidCredentials("Invalid API key"))
      entity <- entityService.getById(entityId).orDieAsUnmanagedFailure
    } yield entity
  }

  override def add(entityId: UUID, apiKey: String): UIO[Unit] = {
    for {
      saltAndApiKey <- ZIO.succeed(apiKeyConfig.salt + apiKey)
      secret <- ZIO
        .fromTry(Try(Sha256Hash.compute(saltAndApiKey.getBytes).hexEncoded))
        .orDie
      _ <- repository
        .insert(entityId, AuthenticationMethodType.ApiKey, secret)
        .orDieAsUnmanagedFailure
    } yield ()
  }

  override def delete(entityId: UUID, apiKey: String): UIO[Unit] = {
    for {
      saltAndApiKey <- ZIO.succeed(apiKeyConfig.salt + apiKey)
      secret <- ZIO
        .fromTry(Try(Sha256Hash.compute(saltAndApiKey.getBytes).hexEncoded))
        .orDie
      _ <- repository.delete(entityId, AuthenticationMethodType.ApiKey, secret)
    } yield ()
  }
}

object ApiKeyAuthenticatorImpl {
  val layer: URLayer[
    ApiKeyConfig & AuthenticationRepository & EntityService & WalletManagementService,
    ApiKeyAuthenticator
  ] =
    ZLayer.fromZIO {
      for {
        apiKeyConfig <- ZIO.service[ApiKeyConfig]
        repository <- ZIO.service[AuthenticationRepository]
        entityService <- ZIO.service[EntityService]
        walletManagementService <- ZIO.service[WalletManagementService]
      } yield ApiKeyAuthenticatorImpl(apiKeyConfig, repository, entityService, walletManagementService)
    }
}
