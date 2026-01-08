package org.hyperledger.identus.agent.walletapi.vault

import io.github.jopenlibs.vault.{Vault, VaultConfig, VaultException}
import io.github.jopenlibs.vault.api.{Logical, LogicalUtilities}
import io.github.jopenlibs.vault.response.LogicalResponse
import zio.*
import zio.http.*
import zio.json.*

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

trait VaultKVClient {
  def get[T: KVCodec](path: String): Task[Option[T]]
  def set[T: KVCodec](path: String, data: T, customMetadata: Map[String, String] = Map.empty): Task[Unit]
}

class VaultKVClientImpl(vaultRef: Ref[(Vault, VaultConfig)], client: Client) extends VaultKVClient {
  import VaultKVClientImpl.*

  override def get[T: KVCodec](path: String): Task[Option[T]] = {
    for {
      vault <- vaultRef.get.map(_._1)
      maybeData <- ZIO
        .attemptBlocking(
          vault
            .logical()
            .read(path)
        )
        .handleVaultErrorOpt("Error reading a secret from Vault.")
        .map(_.map(_.getData().asScala.toMap))
      decodedData <- maybeData.fold(ZIO.none)(data => ZIO.fromTry(summon[KVCodec[T]].decode(data)).asSome)
    } yield decodedData
  }

  override def set[T: KVCodec](path: String, data: T, customMetadata: Map[String, String]): Task[Unit] = {
    val kv = summon[KVCodec[T]].encode(data)
    for {
      vaultWithConfig <- vaultRef.get
      (vault, vaultConfig) = vaultWithConfig
      _ <- ZIO
        .attemptBlocking {
          vault
            .logical()
            .write(path, kv.asJava)
        }
        .handleVaultError("Error writing a secret to Vault.")
      _ <- ExtendedLogical(vaultConfig, client)
        .writeMetadata(path, customMetadata)
        .when(customMetadata.nonEmpty)
    } yield ()
  }
}

object VaultKVClientImpl {

  private final case class TokenRefreshState(leaseDuration: Duration)

  def fromToken(address: String, token: String): RIO[Client, VaultKVClient] =
    for {
      client <- ZIO.service[Client]
      vault <- createVaultInstance(address, Some(token))
      vaultRef <- Ref.make(vault)
    } yield VaultKVClientImpl(vaultRef, client)

  def fromAppRole(address: String, roleId: String, secretId: String): RIO[Client, VaultKVClient] =
    for {
      client <- ZIO.service[Client]
      vault <- createVaultInstance(address)
      vaultRef <- vaultTokenRefreshLogic(vault._1, address, roleId, secretId)
    } yield VaultKVClientImpl(vaultRef, client)

  private def vaultTokenRefreshLogic(
      authVault: Vault,
      address: String,
      roleId: String,
      secretId: String,
      tokenRefreshBuffer: Duration = 15.seconds,
      retrySchedule: Schedule[Any, Any, Any] = Schedule.spaced(5.second) && Schedule.recurs(10)
  ): Task[Ref[(Vault, VaultConfig)]] = {
    val getToken = ZIO
      .attempt {
        val authResponse = authVault.auth().loginByAppRole(roleId, secretId)
        val ttlSecond = authResponse.getAuthLeaseDuration()
        val token = authResponse.getAuthClientToken()
        (token, ttlSecond)
      }
      .retry(retrySchedule)

    for {
      tokenWithTtl <- getToken
      (token, ttlSecond) = tokenWithTtl
      vaultWithToken <- createVaultInstance(address, Some(token))
      vaultRef <- Ref.make(vaultWithToken)
      _ <- ZIO
        .iterate(TokenRefreshState(ttlSecond.seconds))(_ => true) { state =>
          val durationUntilRefresh = state.leaseDuration.minus(tokenRefreshBuffer).max(1.second)
          for {
            _ <- ZIO.sleep(durationUntilRefresh)
            tokenWithTtl <- getToken
            (token, ttlSecond) = tokenWithTtl
            vaultWithToken <- createVaultInstance(address, Some(token))
            _ <- vaultRef.set(vaultWithToken)
          } yield state.copy(leaseDuration = ttlSecond.seconds)
        }
        .fork
    } yield vaultRef
  }

  private def createVaultInstance(address: String, token: Option[String] = None): Task[(Vault, VaultConfig)] =
    ZIO.attempt {
      val configBuilder = VaultConfig()
        .engineVersion(2)
        .address(address)
      token.foreach(configBuilder.token)
      val config = configBuilder.build()
      Vault.create(config) -> config
    }

  extension [R](resp: RIO[R, LogicalResponse]) {

    /** Handle non 200 Vault response as error and 404 as optioanl */
    def handleVaultErrorOpt(message: String): RIO[R, Option[LogicalResponse]] = {
      resp
        .flatMap { resp =>
          val status = resp.getRestResponse().getStatus()
          val bytes = resp.getRestResponse().getBody()
          val body = new String(bytes, StandardCharsets.UTF_8)
          status match {
            case 200 => ZIO.some(resp)
            case 404 => ZIO.none
            case _   =>
              ZIO
                .fail(Exception(s"$message - Got response status code $status, expected 200"))
                .tapError(_ => ZIO.logError(s"$message - Response status: $status. Response body: $body"))
          }
        }
    }

    /** Handle non 200 Vault response as error */
    def handleVaultError(message: String): RIO[R, LogicalResponse] = {
      resp
        .flatMap { resp =>
          val status = resp.getRestResponse().getStatus()
          val bytes = resp.getRestResponse().getBody()
          val body = new String(bytes, StandardCharsets.UTF_8)
          status match {
            case 200 => ZIO.succeed(resp)
            case _   =>
              ZIO
                .fail(Exception(s"$message - Got response status code $status, expected 200"))
                .tapError(_ => ZIO.logError(s"$message - Response status: $status. Response body: $body"))
          }
        }
    }
  }

  private final case class WriteMetadataRequest(
      custom_metadata: Map[String, String]
  )

  private object WriteMetadataRequest {
    given JsonEncoder[WriteMetadataRequest] = JsonEncoder.derived
    given JsonDecoder[WriteMetadataRequest] = JsonDecoder.derived
  }

  private class ExtendedLogical(config: VaultConfig, client: Client) extends Logical(config) {
    // based on https://github.com/jopenlibs/vault-java-driver/blob/e49312a8cbcd14b260dacb2822c19223feb1b7af/src/main/java/io/github/jopenlibs/vault/api/Logical.java#L275
    def writeMetadata(path: String, metadata: Map[String, String]): Task[Unit] = {
      val pathSegments = path.split("/")
      val pathDepth = config.getPrefixPathDepth()
      val adjustedPath = LogicalUtilities.addQualifierToPath(pathSegments.toSeq.asJava, pathDepth, "metadata")
      val url = config.getAddress() + "/v1/" + adjustedPath

      val baseHeaders = Headers(
        Header.ContentType(MediaType.application.json),
        Header.Custom("X-Vault-Request", "true"),
      )
      val additionalHeaders = Headers(
        Seq(
          Option(config.getToken()).map(Header.Custom("X-Vault-Token", _)),
          Option(config.getNameSpace()).map(Header.Custom("X-Vault-Namespace", _)),
        ).flatten
      )

      for {
        url <- ZIO.fromEither(URL.decode(url)).orDie
        request =
          Request(
            url = url,
            method = Method.POST,
            headers = baseHeaders ++ additionalHeaders,
            body = Body.fromString(WriteMetadataRequest(custom_metadata = metadata).toJson)
          )
        _ <- ZIO
          .scoped(client.request(request))
          .timeoutFail(new RuntimeException("Client request timed out"))(5.seconds)
          .flatMap { resp =>
            if (resp.status.isSuccess) ZIO.unit
            else {
              resp.body
                .asString(StandardCharsets.UTF_8)
                .flatMap { body =>
                  ZIO.fail(
                    VaultException(
                      s"Expecting HTTP status 2xx, but instead receiving ${resp.status.code}.\n Response body: ${body}",
                      resp.status.code
                    )
                  )
                }
            }
          }
          .retry {
            val maxRetry = Option(config.getMaxRetries()).getOrElse(0)
            val retryIntervalMillis = Option(config.getRetryIntervalMilliseconds()).getOrElse(0)
            Schedule.recurs(maxRetry) && Schedule.spaced(retryIntervalMillis.millis)
          }
      } yield ()
    }
  }
}
