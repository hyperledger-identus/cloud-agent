package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.model.oid4vci.CredentialIssuer
import org.hyperledger.identus.pollux.core.model.CredentialFormat
import org.hyperledger.identus.pollux.core.service.OID4VCIIssuerMetadataServiceError.{
  CredentialConfigurationNotFound,
  InvalidSchemaId,
  IssuerIdNotFound
}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.{ZIO, ZLayer}
import zio.test.*
import zio.test.Assertion.*

import java.net.{URI, URL}

object OID4VCIIssuerMetadataServiceSpecSuite {

  private def makeCredentialIssuer(authorizationServer: URL): CredentialIssuer = CredentialIssuer(
    authorizationServer = authorizationServer,
    clientId = "client",
    clientSecret = "secret"
  )

  val testSuite = suite("OID4VCIssuerMetadataService")(
    test("get credential issuer successfully") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        authServer1 = URI.create("http://example-1.com").toURL()
        authServer2 = URI.create("http://example-2.com").toURL()
        issuer1 <- service.createCredentialIssuer(makeCredentialIssuer(authServer1))
        issuer2 <- service.createCredentialIssuer(makeCredentialIssuer(authServer2))
        getIssuer1 <- service.getCredentialIssuer(issuer1.id)
        getIssuer2 <- service.getCredentialIssuer(issuer2.id)
        getIssuers <- service.getCredentialIssuers
      } yield assert(getIssuer1)(equalTo(issuer1)) &&
        assert(getIssuer2)(equalTo(issuer2)) &&
        assert(getIssuers)(hasSameElements(Seq(issuer1, issuer2)))
    },
    test("get non-existing credential issuer should fail") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        issuerId <- ZIO.randomWith(_.nextUUID)
        issuers <- service.getCredentialIssuers
        exit <- service.getCredentialIssuer(issuerId).exit
      } yield assert(exit)(failsWithA[IssuerIdNotFound]) &&
        assert(issuers)(isEmpty)
    },
    test("update credential issuer successfully") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        authServer = URI.create("http://example-1.com").toURL()
        issuer <- service.createCredentialIssuer(makeCredentialIssuer(authServer))
        updatedAuthServer = URI.create("http://example-2.com").toURL()
        _ <- service.updateCredentialIssuer(issuer.id, authorizationServer = Some(updatedAuthServer))
        updatedIssuer <- service.getCredentialIssuer(issuer.id)
      } yield assert(updatedIssuer.authorizationServer)(equalTo(updatedAuthServer))
    },
    test("update non-existing credential issuer should fail") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        issuerId <- ZIO.randomWith(_.nextUUID)
        exit <- service.updateCredentialIssuer(issuerId, Some(URI.create("http://example.com").toURL())).exit
      } yield assert(exit)(failsWithA[IssuerIdNotFound])
    },
    test("create credential configuration successfully") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        authServer = URI.create("http://example-1.com").toURL()
        issuer <- service.createCredentialIssuer(makeCredentialIssuer(authServer))
        _ <- service
          .createCredentialConfiguration(
            issuer.id,
            CredentialFormat.JWT,
            "UniversityDegree",
            "resource:///vc-schema-example.json"
          )
        credConfig <- service.getCredentialConfigurationById(issuer.id, "UniversityDegree")
      } yield assert(credConfig.configurationId)(equalTo("UniversityDegree")) &&
        assert(credConfig.format)(equalTo(CredentialFormat.JWT)) &&
        assert(credConfig.schemaId)(equalTo(URI.create("resource:///vc-schema-example.json")))
    },
    test("create credential configuration check for schemaId validity") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        authServer = URI.create("http://example-1.com").toURL()
        issuer <- service.createCredentialIssuer(makeCredentialIssuer(authServer))
        createCredConfig = (schemaId: String) =>
          service
            .createCredentialConfiguration(
              issuer.id,
              CredentialFormat.JWT,
              "UniversityDegree",
              schemaId
            )
        exit1 <- createCredConfig("not a uri").exit
        exit2 <- createCredConfig("http://localhost/schema").exit
      } yield assert(exit1)(failsWithA[InvalidSchemaId]) &&
        assert(exit2)(failsWithA[InvalidSchemaId])
    },
    test("list credential configurations for non-existing issuer should fail") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        issuerId <- ZIO.randomWith(_.nextUUID)
        exit <- service.getCredentialConfigurations(issuerId).exit
      } yield assert(exit)(failsWithA[IssuerIdNotFound])
    },
    test("get non-existing credential configuration should fail") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        issuerId <- ZIO.randomWith(_.nextUUID)
        exit <- service.getCredentialConfigurationById(issuerId, "UniversityDegree").exit
      } yield assert(exit)(failsWithA[CredentialConfigurationNotFound])
    },
    test("delete non-existing credential configuration should fail") {
      for {
        service <- ZIO.service[OID4VCIIssuerMetadataService]
        issuerId <- ZIO.randomWith(_.nextUUID)
        exit <- service.deleteCredentialConfiguration(issuerId, "UniversityDegree").exit
      } yield assert(exit)(failsWithA[CredentialConfigurationNotFound])
    },
  ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

}
