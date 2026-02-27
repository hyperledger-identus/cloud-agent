package org.hyperledger.identus.oid4vci.domain

import com.nimbusds.jose.*
import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.agent.walletapi.service.{ManagedDIDService, MockManagedDIDService}
import org.hyperledger.identus.agent.walletapi.storage.{DIDNonSecretStorage, MockDIDNonSecretStorage}
import org.hyperledger.identus.castor.core.model.did.{DID, PrismDID, VerificationRelationship}
import org.hyperledger.identus.castor.core.service.{DIDService, MockDIDService}
import org.hyperledger.identus.oid4vci.http.{ClaimDescriptor, CredentialDefinition, Localization}
import org.hyperledger.identus.oid4vci.service.{OIDCCredentialIssuerService, OIDCCredentialIssuerServiceImpl}
import org.hyperledger.identus.oid4vci.storage.InMemoryIssuanceSessionService
import org.hyperledger.identus.pollux.core.model.oid4vci.CredentialConfiguration
import org.hyperledger.identus.pollux.core.model.CredentialFormat
import org.hyperledger.identus.pollux.core.repository.{
  CredentialRepositoryInMemory,
  CredentialStatusListRepositoryInMemory
}
import org.hyperledger.identus.pollux.core.service.*
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.vc.jwt.PrismDidResolver
import org.hyperledger.identus.shared.messaging.{MessagingService, MessagingServiceConfig, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.{Clock, Random, URLayer, ZIO, ZLayer}
import zio.json.ast.Json
import zio.mock.MockSpecDefault
import zio.test.*
import zio.test.Assertion.*

import java.net.URI
import java.time.Instant
import java.util.UUID
import scala.util.Try

object OIDCCredentialIssuerServiceSpec
    extends MockSpecDefault
    with CredentialServiceSpecHelper
    with Openid4VCIProofJwtOps {

  val layers: URLayer[
    DIDService & ManagedDIDService & DIDNonSecretStorage & OID4VCIIssuerMetadataService,
    CredentialService & CredentialDefinitionService & OIDCCredentialIssuerService
  ] =
    ZLayer.makeSome[
      DIDService & ManagedDIDService & DIDNonSecretStorage & OID4VCIIssuerMetadataService,
      CredentialService & CredentialDefinitionService & OIDCCredentialIssuerService
    ](
      InMemoryIssuanceSessionService.layer,
      CredentialRepositoryInMemory.layer,
      CredentialStatusListRepositoryInMemory.layer,
      PrismDidResolver.layer,
      ResourceUrlResolver.layer,
      credentialDefinitionServiceLayer,
      GenericSecretStorageInMemory.layer,
      LinkSecretServiceImpl.layer,
      CredentialServiceImpl.layer,
      (MessagingServiceConfig.inMemoryLayer >>> MessagingService.serviceLayer >>>
        (zio.Scope.default >>> MessagingService.producerLayer[UUID, WalletIdAndRecordId])).orDie,
      OIDCCredentialIssuerServiceImpl.layer
    )

  override def spec = suite("CredentialServiceImpl")(
    oid4vciCredentialIssuerServiceSpec,
    validateProofSpec
  )

  private val (_, issuerKp, issuerDidMetadata, issuerDidData) =
    MockDIDService.createDIDOIDC(VerificationRelationship.AssertionMethod)

  private val (holderOp, holderKp, holderDidMetadata, holderDidData) =
    MockDIDService.createDIDOIDC(VerificationRelationship.AssertionMethod)

  private val holderDidServiceExpectations =
    MockDIDService.resolveDIDExpectation(holderDidMetadata, holderDidData)

  private val issuerDidServiceExpectations =
    MockDIDService.resolveDIDExpectation(issuerDidMetadata, issuerDidData)

  private val issuerManagedDIDServiceExpectations =
    MockManagedDIDService.findDIDKeyPairExpectation(issuerKp)

  private val getIssuerPrismDidWalletIdExpectations =
    MockDIDNonSecretStorage.getPrismDidWalletIdExpectation(issuerDidData.id, WalletId.default)

  private val getCredentialConfigurationExpectations =
    MockOID4VCIIssuerMetadataService.getCredentialConfigurationByIdExpectations(
      CredentialConfiguration(
        configurationId = "DrivingLicense",
        format = CredentialFormat.JWT,
        schemaId = URI("resource:///vc-schema-example.json"),
        createdAt = Instant.EPOCH
      )
    )

  private def buildJwtProof(nonce: String, aud: UUID, iat: Int) = {
    val longFormDid = PrismDID.buildLongFormFromOperation(holderOp)
    val keyIndex = holderDidData.publicKeys.find(_.purpose == VerificationRelationship.AssertionMethod).get.id
    val kid = longFormDid.toString + "#" + keyIndex
    makeJwtProof(kid, nonce, aud, iat, holderKp.privateKey)
  }

  private val validateProofSpec = suite("Validate holder's proof of possession using the LongFormPrismDID")(
    test("should validate the holder's proof of possession using the LongFormPrismDID") {
      for {
        credentialIssuer <- ZIO.service[OIDCCredentialIssuerService]
        nonce <- Random.nextString(10)
        aud <- Random.nextUUID
        iat <- Clock.instant.map(_.getEpochSecond.toInt)
        jwt = buildJwtProof(nonce, aud, iat)
        result <- credentialIssuer.verifyJwtProof(jwt)
      } yield assert(result)(equalTo(true))
    }.provide(
      holderDidServiceExpectations.toLayer,
      MockManagedDIDService.empty,
      MockDIDNonSecretStorage.empty,
      MockOID4VCIIssuerMetadataService.empty,
      layers
    )
  )

  private val oid4vciCredentialIssuerServiceSpec =
    suite("Simple JWT credential issuance")(
      test("should issue a JWT credential") {
        for {
          oidcCredentialIssuerService <- ZIO.service[OIDCCredentialIssuerService]
          credentialDefinition = CredentialDefinition(
            `@context` = Some(Seq("https://www.w3.org/2018/credentials/v1")),
            `type` = Seq("VerifiableCredential", "CertificateOfBirth"),
            credentialSubject = Some(
              Map(
                "name" ->
                  ClaimDescriptor(mandatory = Some(true), valueType = Some("string"), display = Seq.empty[Localization])
              )
            )
          )
          subjectDID <- ZIO.fromEither(DID.fromString("did:work:MDP8AsFhHzhwUvGNuYkX7T"))
          jwt <- oidcCredentialIssuerService
            .issueJwtCredential(
              issuerDidData.id,
              Some(subjectDID),
              Json("name" -> Json.Str("Alice")),
              None,
              credentialDefinition
            )
          jwtObject <- ZIO.fromTry(Try(JWSObject.parse(jwt.value)))
          payload <- ZIO.fromEither(Json.decoder.decodeJson(jwtObject.getPayload.toString).flatMap(_.as[Json.Obj]))
          vc <- ZIO.fromEither(payload.get("vc").get.as[Json.Obj])
          credentialSubject <- ZIO.fromEither(vc.get("credentialSubject").get.as[Json.Obj])
          name <- ZIO.fromEither(credentialSubject.get("name").get.as[String])
        } yield assert(jwt.value)(isNonEmptyString) &&
          // assert(jwtObject.getHeader.getKeyID)(equalTo(issuerDidData.id.toString)) && //TODO: add key ID to the header
          assert(jwtObject.getHeader.getAlgorithm)(equalTo(JWSAlgorithm.ES256K)) &&
          assert(name)(equalTo("Alice"))
      }.provide(
        issuerDidServiceExpectations.toLayer,
        issuerManagedDIDServiceExpectations.toLayer,
        getIssuerPrismDidWalletIdExpectations.toLayer,
        MockOID4VCIIssuerMetadataService.empty,
        layers
      ),
      test("create credential-offer with valid claims and schemaId") {
        val wac = ZLayer.succeed(WalletAccessContext(WalletId.random))
        val claims = Json(
          "credentialSubject" -> Json.Obj(
            "emailAddress" -> Json.Str("alice@example.com"),
            "givenName" -> Json.Str("Alice"),
            "familyName" -> Json.Str("Wonderland"),
            "dateOfIssuance" -> Json.Str("2000-01-01T10:00:00Z"),
            "drivingLicenseID" -> Json.Str("12345"),
            "drivingClass" -> Json.Num(5),
          )
        )
        for {
          oidcCredentialIssuerService <- ZIO.service[OIDCCredentialIssuerService]
          offer <- oidcCredentialIssuerService
            .createCredentialOffer(
              URI("http://example.com").toURL(),
              UUID.randomUUID(),
              "DrivingLicense",
              issuerDidData.id,
              claims,
            )
            .provide(wac)
          issuerState = offer.grants.get.authorization_code.issuer_state.get
          session <- oidcCredentialIssuerService.getIssuanceSessionByIssuerState(issuerState)
        } yield assert(session.claims)(equalTo(claims)) &&
          assert(session.schemaId)(isSome(equalTo("resource:///vc-schema-example.json")))
      }.provide(
        MockDIDService.empty,
        MockManagedDIDService.empty,
        MockDIDNonSecretStorage.empty,
        getCredentialConfigurationExpectations.toLayer,
        layers
      ),
      test("reject credential-offer when created with invalid claims") {
        val wac = ZLayer.succeed(WalletAccessContext(WalletId.random))
        val claims = Json("credentialSubject" -> Json.Obj("foo" -> Json.Str("bar")))
        for {
          oidcCredentialIssuerService <- ZIO.service[OIDCCredentialIssuerService]
          exit <- oidcCredentialIssuerService
            .createCredentialOffer(
              URI("http://example.com").toURL(),
              UUID.randomUUID(),
              "DrivingLicense",
              issuerDidData.id,
              claims,
            )
            .provide(wac)
            .exit
        } yield assert(exit)(failsWithA[OIDCCredentialIssuerService.Errors.CredentialSchemaError])
      }.provide(
        MockDIDService.empty,
        MockManagedDIDService.empty,
        MockDIDNonSecretStorage.empty,
        getCredentialConfigurationExpectations.toLayer,
        layers
      ),
    )
}
