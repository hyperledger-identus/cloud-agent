package org.hyperledger.identus.agent.server.config

import monocle.syntax.all.*
import org.hyperledger.identus.agent.server.SystemModule
import zio.*
import zio.test.*
import zio.test.Assertion.*

object AppConfigSpec extends ZIOSpecDefault {

  private val baseVaultConfig = VaultConfig(
    address = "http://localhost:8200",
    token = None,
    appRoleRoleId = None,
    appRoleSecretId = None,
    useSemanticPath = true,
  )
  // private val baseInvalidHttpEndpointConfig = java.net.URL("http://:8080")

  private val basePrismDriverVdrConfig = PrismDriverVdrConfig(
    blockfrostApiKey = "api-key",
    walletMnemonic = "",
    walletPassphrase = "passphrase",
    didPrism = "did:prism:123",
    vdrPrivateKey = "abcdef",
    stateDir = ""
  )

  private val baseVdrConfig = VdrConfig(
    inMemoryDriverEnabled = false,
    databaseDriverEnabled = false,
    prismDriverEnabled = false,
    prismDriver = None
  )

  override def spec = suite("AppConfigSpec")(
    test("load config successfully") {
      for {
        appConfig <- ZIO.serviceWith[AppConfig](
          _.focus(_.agent.secretStorage.backend)
            .replace(SecretStorageBackend.postgres)
        )
      } yield assert(appConfig.validate)(isRight(anything))
    },
    test("reject config when use vault secret storage and config is empty") {
      for {
        appConfig <- ZIO.serviceWith[AppConfig](
          _.focus(_.agent.secretStorage.backend)
            .replace(SecretStorageBackend.vault)
            .focus(_.agent.secretStorage.vault)
            .replace(None)
        )
      } yield assert(appConfig.validate)(isLeft(containsString("config is not provided")))
    },
    test("reject config when use vault secret storage and authentication is not provided") {
      for {
        appConfig <- ZIO.serviceWith[AppConfig](
          _.focus(_.agent.secretStorage.backend)
            .replace(SecretStorageBackend.vault)
            .focus(_.agent.secretStorage.vault)
            .replace(Some(baseVaultConfig))
        )
      } yield assert(appConfig.validate)(isLeft(containsString("authentication must be provided")))
    },
    test("load config when use vault secret storage with token authentication") {
      for {
        appConfig <- ZIO.serviceWith[AppConfig](
          _.focus(_.agent.secretStorage.backend)
            .replace(SecretStorageBackend.vault)
            .focus(_.agent.secretStorage.vault)
            .replace(Some(baseVaultConfig.copy(token = Some("token"))))
        )
      } yield assert(appConfig.validate)(isRight(anything))
    },
    test("load config when use vault secret storage with appRole authentication") {
      for {
        appConfig <- ZIO.serviceWith[AppConfig](
          _.focus(_.agent.secretStorage.backend)
            .replace(SecretStorageBackend.vault)
            .focus(_.agent.secretStorage.vault)
            .replace(Some(baseVaultConfig.copy(appRoleRoleId = Some("roleId"), appRoleSecretId = Some("secretId"))))
        )
      } yield assert(appConfig.validate)(isRight(anything))
    },
    test("prefer vault token authentication when multiple auth methods are provided") {
      val vaultConfig = baseVaultConfig.copy(
        token = Some("token"),
        appRoleRoleId = Some("roleId"),
        appRoleSecretId = Some("secretId"),
      )
      assert(vaultConfig.validate)(isRight(isSubtype[ValidatedVaultConfig.TokenAuth](anything)))
    },
    test("fail when prismDriverEnabled is true and prismDriver is None") {
      val vdrConfig = baseVdrConfig.copy(prismDriverEnabled = true, prismDriver = None)
      assert(vdrConfig.validate)(isLeft(containsString("config is not provided")))
    },
    test("pass when prismDriverEnabled is true and prismDriver is Some") {
      val vdrConfig = baseVdrConfig.copy(prismDriverEnabled = true, prismDriver = Some(basePrismDriverVdrConfig))
      assert(vdrConfig.validate)(isRight(anything))
    },
    test("pass when prismDriverEnabled is false and prismDriver is None") {
      val vdrConfig = baseVdrConfig.copy(prismDriverEnabled = false, prismDriver = None)
      assert(vdrConfig.validate)(isRight(anything))
    },
    test("pass when prismDriverEnabled is false and prismDriver is Some") {
      val vdrConfig = baseVdrConfig.copy(prismDriverEnabled = false, prismDriver = Some(basePrismDriverVdrConfig))
      assert(vdrConfig.validate)(isRight(anything))
    }
  ).provide(SystemModule.configLayer) + {

    import AppConfig.given
    import zio.config.magnolia.*
    val didCommEndpointConfig: Config[DidCommEndpointConfig] = deriveConfig[DidCommEndpointConfig]

    suite("DidCommEndpointConfig URL type")(
      test("DidCommEndpointConfig that the correct format") {
        {
          for {
            didCommEndpointConfig <- ZIO.service[DidCommEndpointConfig]
          } yield assert(true)(isTrue)
        }.provide(
          ZLayer.fromZIO(
            ConfigProvider
              .fromMap(Map("http.port" -> "9999", "publicEndpointUrl" -> "http://example:8080/path"))
              .load(didCommEndpointConfig)
          )
        )
      },
      test("reject config when invalid http didcomm service endpoint url provided") {

        assertZIO(
          ConfigProvider
            .fromMap(Map("http.port" -> "9999", "publicEndpointUrl" -> "http://:8080/path"))
            .load(didCommEndpointConfig)
            .exit
        )(fails(isSubtype[Config.Error.InvalidData](anything)))
        // Config.Error.InvalidData(zio.Chunk("publicEndpointUrl"), "Invalid URL: http://:8080/path")
      },
    )
  } + {
    import zio.config.magnolia.deriveConfig
    case class TestSecretStorageBackend(t: SecretStorageBackend)
    val secretStorageBackendConfig: Config[TestSecretStorageBackend] = deriveConfig[TestSecretStorageBackend]

    suite("SecretStorageBackend enum test deriveConfig")(
      test("test SecretStorageBackend is postgres") {
        {
          for {
            secretStorageBackend <- ZIO.service[TestSecretStorageBackend]
            _ <- ZIO.log(secretStorageBackend.toString())
          } yield assertTrue(secretStorageBackend.t == SecretStorageBackend.postgres)
        }.provide(
          ZLayer.fromZIO(
            ConfigProvider
              .fromMap(Map("t" -> "postgres"))
              .load(secretStorageBackendConfig)
          )
        )
      },
      test("test SecretStorageBackend is not vault") {
        {
          for {
            secretStorageBackend <- ZIO.service[TestSecretStorageBackend]
            _ <- ZIO.log(secretStorageBackend.toString())
          } yield assertTrue(secretStorageBackend.t != SecretStorageBackend.vault)
        }.provide(
          ZLayer.fromZIO(
            ConfigProvider
              .fromMap(Map("t" -> "postgres"))
              .load(secretStorageBackendConfig)
          )
        )
      },
    )
  }
}
