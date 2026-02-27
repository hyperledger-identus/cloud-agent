package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.core.model.oid4vci.{CredentialConfiguration, CredentialIssuer}
import org.hyperledger.identus.pollux.core.model.CredentialFormat
import org.hyperledger.identus.shared.db.Errors.UnexpectedAffectedRow
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.{ZIO, ZLayer}
import zio.test.*
import zio.test.Assertion.*

import java.net.{URI, URL}
import java.time.Instant

object OID4VCIIssuerMetadataRepositorySpecSuite {

  private val credConfig = CredentialConfiguration(
    configurationId = "DrivingLicense",
    format = CredentialFormat.JWT,
    schemaId = URI.create("http://example.com/schema"),
    createdAt = Instant.now
  ).withTruncatedTimestamp()

  private def makeCredentialIssuer(authorizationServer: URL): CredentialIssuer = CredentialIssuer(
    authorizationServer = authorizationServer,
    clientId = "client",
    clientSecret = "secret"
  )

  private def initMultiWalletIssuers = {
    val walletId1 = WalletId.random
    val walletId2 = WalletId.random
    val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
    val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
    for {
      repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
      authServer1 = URI.create("http://example-1.com").toURL()
      authServer2 = URI.create("http://example-2.com").toURL()
      issuer1 = makeCredentialIssuer(authorizationServer = authServer1)
      issuer2 = makeCredentialIssuer(authorizationServer = authServer2)
      _ <- repo.createIssuer(issuer1).provide(wallet1)
      _ <- repo.createIssuer(issuer2).provide(wallet2)
    } yield (issuer1, wallet1, issuer2, wallet2)
  }

  val testSuite = suite("CRUD operations")(
    test("find non-existing credential issuers return None") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        issuerId <- ZIO.randomWith(_.nextUUID)
        maybeIssuer <- repo.findIssuerById(issuerId)
      } yield assert(maybeIssuer)(isNone)
    },
    test("create credential issuers successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        authServer2 = URI.create("http://example-2.com").toURL()
        issuer1 = makeCredentialIssuer(authorizationServer = authServer1)
        issuer2 = makeCredentialIssuer(authorizationServer = authServer2)
        _ <- repo.createIssuer(issuer1)
        _ <- repo.createIssuer(issuer2)
        maybeIssuer1 <- repo.findIssuerById(issuer1.id)
        maybeIssuer2 <- repo.findIssuerById(issuer2.id)
      } yield assert(maybeIssuer1)(isSome(equalTo(issuer1))) &&
        assert(maybeIssuer2)(isSome(equalTo(issuer2)))
    },
    test("create credential issuers with same id should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        issuer1 = makeCredentialIssuer(authorizationServer = authServer1)
        _ <- repo.createIssuer(issuer1)
        exit <- repo.createIssuer(issuer1).exit
      } yield assert(exit)(dies(anything))
    },
    test("delete credential issuer successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        _ <- repo.createIssuer(issuer)
        _ <- repo.deleteIssuer(issuer.id)
        maybeIssuer <- repo.findIssuerById(issuer.id)
      } yield assert(maybeIssuer)(isNone)
    },
    test("delete non-existing credential issuer should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        exit <- repo.deleteIssuer(issuer.id).exit
      } yield assert(exit)(dies(isSubtype[UnexpectedAffectedRow](anything)))
    },
    test("update credential issuer successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        authServer2 = URI.create("http://example-2.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer1)
        _ <- repo.createIssuer(issuer)
        _ <- repo.updateIssuer(
          issuerId = issuer.id,
          authorizationServer = Some(authServer2),
          authorizationServerClientId = Some("client-2"),
          authorizationServerClientSecret = Some("secret-2")
        )
        updatedIssuer <- repo.findIssuerById(issuer.id).some
      } yield assert(updatedIssuer.id)(equalTo(issuer.id)) &&
        assert(updatedIssuer.authorizationServer)(equalTo(authServer2)) &&
        assert(updatedIssuer.authorizationServerClientId)(equalTo("client-2")) &&
        assert(updatedIssuer.authorizationServerClientSecret)(equalTo("secret-2")) &&
        assert(updatedIssuer.updatedAt)(not(equalTo(issuer.createdAt)))
    },
    test("update credential issuer with empty patch successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        authServer2 = URI.create("http://example-2.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer1)
        _ <- repo.createIssuer(issuer)
        _ <- repo.updateIssuer(issuer.id) // empty patch
        updatedIssuer <- repo.findIssuerById(issuer.id).some
      } yield assert(updatedIssuer.id)(equalTo(issuer.id)) &&
        assert(updatedIssuer.authorizationServer)(equalTo(issuer.authorizationServer)) &&
        assert(updatedIssuer.createdAt)(equalTo(issuer.createdAt))
    },
    test("update non-existing credential issuer should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        issuerId <- ZIO.randomWith(_.nextUUID)
        authServer = URI.create("http://example-1.com").toURL()
        exit <- repo.updateIssuer(issuerId, authorizationServer = Some(authServer)).exit
      } yield assert(exit)(dies(isSubtype[UnexpectedAffectedRow](anything)))
    },
    test("create credential configuration successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        _ <- repo.createIssuer(issuer)
        _ <- repo.createCredentialConfiguration(issuer.id, credConfig)
        maybeCredConfig <- repo.findCredentialConfigurationById(issuer.id, credConfig.configurationId)
      } yield assert(maybeCredConfig)(isSome(equalTo(credConfig)))
    },
    test("create credential configuration with same id should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer).withTruncatedTimestamp()
        _ <- repo.createIssuer(issuer)
        _ <- repo.createCredentialConfiguration(issuer.id, credConfig)
        exit <- repo.createCredentialConfiguration(issuer.id, credConfig).exit
      } yield assert(exit)(dies(anything))
    },
    test("create credential configuration with same id for different issuer successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        authServer2 = URI.create("http://example-2.com").toURL()
        issuer1 = makeCredentialIssuer(authorizationServer = authServer1)
        issuer2 = makeCredentialIssuer(authorizationServer = authServer2)
        _ <- repo.createIssuer(issuer1)
        _ <- repo.createIssuer(issuer2)
        _ <- repo.createCredentialConfiguration(issuer1.id, credConfig)
        _ <- repo.createCredentialConfiguration(issuer2.id, credConfig)
        maybeCredConfig1 <- repo.findCredentialConfigurationById(issuer1.id, credConfig.configurationId)
        maybeCredConfig2 <- repo.findCredentialConfigurationById(issuer2.id, credConfig.configurationId)
      } yield assert(maybeCredConfig1)(isSome(equalTo(credConfig))) &&
        assert(maybeCredConfig2)(isSome(equalTo(credConfig)))
    },
    test("create credential configuration for non-existing issuer should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        issuerId <- ZIO.randomWith(_.nextUUID)
        exit <- repo.createCredentialConfiguration(issuerId, credConfig).exit
      } yield assert(exit)(dies(anything))
    },
    test("list credential configurations successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer1 = URI.create("http://example-1.com").toURL()
        issuer1 = makeCredentialIssuer(authorizationServer = authServer1)
        issuer2 = makeCredentialIssuer(authorizationServer = authServer1)
        credConfig1 = credConfig.copy(configurationId = "DrivingLicense")
        credConfig2 = credConfig.copy(configurationId = "UniversityDegree")
        credConfig3 = credConfig.copy(configurationId = "TrainingCertificate")
        _ <- repo.createIssuer(issuer1)
        _ <- repo.createIssuer(issuer2)
        _ <- repo.createCredentialConfiguration(issuer1.id, credConfig1)
        _ <- repo.createCredentialConfiguration(issuer1.id, credConfig2)
        _ <- repo.createCredentialConfiguration(issuer2.id, credConfig3)
        credConfigs1 <- repo.findCredentialConfigurationsByIssuer(issuer1.id)
        credConfigs2 <- repo.findCredentialConfigurationsByIssuer(issuer2.id)
      } yield assert(credConfigs1)(hasSameElements(Seq(credConfig1, credConfig2))) &&
        assert(credConfigs2)(hasSameElements(Seq(credConfig3)))
    },
    test("find and list return empty result for non-existing configurations") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        _ <- repo.createIssuer(issuer)
        maybeCredConfig <- repo.findCredentialConfigurationById(issuer.id, credConfig.configurationId)
        credConfigs <- repo.findCredentialConfigurationsByIssuer(issuer.id)
      } yield assert(maybeCredConfig)(isNone) &&
        assert(credConfigs)(isEmpty)
    },
    test("delete credential configuration successfully") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        _ <- repo.createIssuer(issuer)
        _ <- repo.createCredentialConfiguration(issuer.id, credConfig)
        maybeCredConfig1 <- repo.findCredentialConfigurationById(issuer.id, credConfig.configurationId)
        _ <- repo.deleteCredentialConfiguration(issuer.id, credConfig.configurationId)
        maybeCredConfig2 <- repo.findCredentialConfigurationById(issuer.id, credConfig.configurationId)
      } yield assert(maybeCredConfig1)(isSome(equalTo(credConfig))) &&
        assert(maybeCredConfig2)(isNone)
    },
    test("delete non-existing creential configuration should fail") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        randomId <- ZIO.randomWith(_.nextUUID)
        _ <- repo.createIssuer(issuer)
        _ <- repo.createCredentialConfiguration(issuer.id, credConfig)
        exit1 <- repo.deleteCredentialConfiguration(issuer.id, "ExampleLicense").exit
        exit2 <- repo.deleteCredentialConfiguration(randomId, "ExampleLicense").exit
      } yield assert(exit1)(diesWithA[UnexpectedAffectedRow]) &&
        assert(exit2)(diesWithA[UnexpectedAffectedRow])
    },
    test("delete issuer also delete credential configuration") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        authServer = URI.create("http://example-1.com").toURL()
        issuer = makeCredentialIssuer(authorizationServer = authServer)
        _ <- repo.createIssuer(issuer)
        _ <- repo.createCredentialConfiguration(issuer.id, credConfig)
        _ <- repo.deleteIssuer(issuer.id)
        maybeCredConfig <- repo.findCredentialConfigurationById(issuer.id, credConfig.configurationId)
      } yield assert(maybeCredConfig)(isNone)
    }
  ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

  val multitenantTestSuite = suite("multi-tenant CRUD operation")(
    test("list only issuers inside wallet") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        init <- initMultiWalletIssuers
        (issuer1, wallet1, issuer2, wallet2) = init
        issuers1 <- repo.findWalletIssuers.provide(wallet1)
        issuers2 <- repo.findWalletIssuers.provide(wallet2)
      } yield assert(issuers1)(hasSameElements(Seq(issuer1))) &&
        assert(issuers2)(hasSameElements(Seq(issuer2)))
    },
    test("update issuer across wallet is not allowed") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        init <- initMultiWalletIssuers
        (issuer1, wallet1, issuer2, wallet2) = init
        exit <- repo.updateIssuer(issuer1.id).provide(wallet2).exit
      } yield assert(exit)(diesWithA[UnexpectedAffectedRow])
    },
    test("delete issuer across wallet is not allowed") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        init <- initMultiWalletIssuers
        (issuer1, wallet1, issuer2, wallet2) = init
        exit <- repo.deleteIssuer(issuer1.id).provide(wallet2).exit
      } yield assert(exit)(diesWithA[UnexpectedAffectedRow])
    },
    test("create credential configuration across wallet is not allowed") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        init <- initMultiWalletIssuers
        (issuer1, wallet1, issuer2, wallet2) = init
        exit <- repo.createCredentialConfiguration(issuer1.id, credConfig).provide(wallet2).exit
      } yield assert(exit)(dies(anything))
    },
    test("delete credential configuration across wallet is not allowed") {
      for {
        repo <- ZIO.service[OID4VCIIssuerMetadataRepository]
        init <- initMultiWalletIssuers
        (issuer1, wallet1, issuer2, wallet2) = init
        exit <- repo.deleteCredentialConfiguration(issuer1.id, credConfig.configurationId).provide(wallet2).exit
      } yield assert(exit)(diesWithA[UnexpectedAffectedRow])
    },
  )

}
