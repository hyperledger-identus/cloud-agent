package org.hyperledger.identus.pollux.credentialdefinition

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.typesafe.config.ConfigFactory
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.http.CustomServerInterceptors
import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.agent.walletapi.model.{BaseEntity, ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.service.{ManagedDIDService, MockManagedDIDService}
import org.hyperledger.identus.agent.walletapi.storage.GenericSecretStorage
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.castor.core.model.did.PrismDIDOperation
import org.hyperledger.identus.iam.authentication.{AuthenticatorWithAuthZ, DefaultEntityAuthenticator}
import org.hyperledger.identus.pollux.core.repository.CredentialDefinitionRepository
import org.hyperledger.identus.pollux.core.service.{CredentialDefinitionService, CredentialDefinitionServiceImpl}
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.credentialdefinition.controller.{
  CredentialDefinitionController,
  CredentialDefinitionControllerImpl
}
import org.hyperledger.identus.pollux.credentialdefinition.http.{
  CredentialDefinitionInput,
  CredentialDefinitionResponse,
  CredentialDefinitionResponsePage
}
import org.hyperledger.identus.pollux.sql.repository.JdbcCredentialDefinitionRepository
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import sttp.client3.{basicRequest, DeserializationException, Response, UriContext}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.ziojson.*
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.RIOMonadError
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.json.EncoderOps
import zio.mock.Expectation
import zio.test.{Assertion, Gen, ZIOSpecDefault}

import java.time.OffsetDateTime

trait CredentialDefinitionTestTools extends PostgresTestContainerSupport {
  self: ZIOSpecDefault =>

  type CredentialDefinitionBadRequestResponse =
    Response[Either[DeserializationException[String], ErrorResponse]]
  type CredentialDefinitionResponseType =
    Response[Either[DeserializationException[String], CredentialDefinitionResponse]]
  type CredentialDefinitionResponsePageType =
    Response[
      Either[DeserializationException[String], CredentialDefinitionResponsePage]
    ]

  private val controllerLayer =
    GenericSecretStorageInMemory.layer >+>
      systemTransactorLayer >+> contextAwareTransactorLayer >+> JdbcCredentialDefinitionRepository.layer >+>
      ResourceUrlResolver.layer >+>
      CredentialDefinitionServiceImpl.layer >+>
      CredentialDefinitionControllerImpl.layer

  val mockManagedDIDServiceLayer: Expectation[ManagedDIDService] = MockManagedDIDService
    .GetManagedDIDState(
      assertion = Assertion.anything,
      result = Expectation.value(
        Some(
          ManagedDIDState(
            PrismDIDOperation.Create(Nil, Nil, Nil),
            0,
            PublicationState.Published(scala.collection.immutable.ArraySeq.empty)
          )
        )
      )
    )

  val authenticatorLayer: TaskLayer[AuthenticatorWithAuthZ[BaseEntity]] = DefaultEntityAuthenticator.layer

  val configLayer = ZLayer.fromZIO(
    TypesafeConfigProvider
      .fromTypesafeConfig(ConfigFactory.load())
      .load(AppConfig.config)
  )

  lazy val testEnvironmentLayer = ZLayer.makeSome[
    ManagedDIDService,
    CredentialDefinitionController & CredentialDefinitionRepository & CredentialDefinitionService &
      PostgreSQLContainer & AuthenticatorWithAuthZ[BaseEntity] & GenericSecretStorage & AppConfig
  ](
    controllerLayer,
    pgContainerLayer,
    authenticatorLayer,
    configLayer
  )

  val credentialDefinitionUriBase = uri"http://test.com/credential-definition-registry/definitions"

  def bootstrapOptions[F[_]](monadError: MonadError[F]) = {
    new CustomiseInterceptors[F, Any](_ => ())
      .exceptionHandler(CustomServerInterceptors.tapirExceptionHandler)
      .rejectHandler(CustomServerInterceptors.tapirRejectHandler)
      .decodeFailureHandler(CustomServerInterceptors.tapirDecodeFailureHandler)
  }

  def httpBackend(
      config: AppConfig,
      controller: CredentialDefinitionController,
      authenticator: AuthenticatorWithAuthZ[BaseEntity]
  ) = {

    val credentialDefinitionRegistryEndpoints =
      CredentialDefinitionRegistryServerEndpoints(config, controller, authenticator, authenticator)

    val backend =
      TapirStubInterpreter(
        bootstrapOptions(new RIOMonadError[Any]),
        SttpBackendStub(new RIOMonadError[Any])
      )
        .whenServerEndpoint(credentialDefinitionRegistryEndpoints.create.http)
        .thenRunLogic()
        .whenServerEndpoint(credentialDefinitionRegistryEndpoints.get.http)
        .thenRunLogic()
        .whenServerEndpoint(
          credentialDefinitionRegistryEndpoints.getMany.http
        )
        .thenRunLogic()
        .backend()
    backend
  }

  def deleteAllCredentialDefinitions: RIO[CredentialDefinitionRepository & WalletAccessContext, Unit] = {
    for {
      repository <- ZIO.service[CredentialDefinitionRepository]
      result <- repository.deleteAll()
    } yield result
  }
}

trait CredentialDefinitionGen {
  self: ZIOSpecDefault & CredentialDefinitionTestTools =>
  object Generator {
    val credentialDefinitionName = Gen.alphaNumericStringBounded(4, 12)
    val majorVersion = Gen.int(1, 9)
    val minorVersion = Gen.int(0, 9)
    val patchVersion = Gen.int(0, 9)
    val credentialDefinitionVersion =
      majorVersion <*> minorVersion <*> patchVersion map (v => s"${v._1}.${v._2}.${v._3}")
    val credentialDefinitionDescription = Gen.alphaNumericStringBounded(5, 30)
    val credentialDefinitionAttribute = Gen.alphaNumericStringBounded(3, 9)
    val credentialDefinitionAttributes = Gen.setOfBounded(1, 4)(credentialDefinitionAttribute).map(_.toList)
    val credentialDefinitionAuthored = Gen.offsetDateTime(
      min = OffsetDateTime.now().minusMonths(6),
      max = OffsetDateTime.now()
    )
    val credentialDefinitionTag: Gen[Any, String] = Gen.alphaNumericStringBounded(3, 5)

    val credentialDefinitionAuthor =
      Gen.int(1000000, 9999999).map(i => s"did:prism:4fb06243213500578f59588de3e1dd9b266ec1b61e43b0ff86ad0712f$i")

    val credentialDefinitionSchemaId = Gen.alphaNumericStringBounded(4, 12)
    val credentialDefinitionSignatureType = Gen.alphaNumericStringBounded(4, 12)
    val credentialDefinitionSupportRevocation = Gen.boolean

    val definitionString =
      """
        |{
        |   "credentialSubject": {
        |     "emailAddress": "alice@wonderland.com",
        |     "givenName": "Alice",
        |     "familyName": "Wonderland",
        |     "dateOfIssuance": "2000-01-01T10:00:00Z",
        |     "drivingLicenseID": "12345",
        |     "drivingClass": 5
        |   }
        |}
        |""".stripMargin

    val credentialDefinitionInput = for {
      name <- credentialDefinitionName
      version <- credentialDefinitionVersion
      description <- credentialDefinitionDescription
      author <- credentialDefinitionAuthor
      tag <- credentialDefinitionTag
      signatureType <- credentialDefinitionSignatureType
      supportRevocation <- credentialDefinitionSupportRevocation
    } yield CredentialDefinitionInput(
      name = name,
      version = version,
      description = Some(description),
      tag = tag,
      author = author,
      schemaId = "resource:///anoncred-schema-example.json",
      signatureType = signatureType,
      supportRevocation = supportRevocation
    )
  }

  def generateCredentialDefinitionsN(
      count: Int
  ): ZIO[CredentialDefinitionController & AppConfig & AuthenticatorWithAuthZ[BaseEntity], Throwable, List[
    CredentialDefinitionInput
  ]] =
    for {
      controller <- ZIO.service[CredentialDefinitionController]
      authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
      config <- ZIO.service[AppConfig]
      backend = httpBackend(config, controller, authenticator)
      inputs <- Generator.credentialDefinitionInput.runCollectN(count)
      _ <- inputs
        .map(in =>
          basicRequest
            .post(credentialDefinitionUriBase)
            .body(in.toJsonPretty)
            .response(asJsonAlways[CredentialDefinitionResponse])
            .send(backend)
        )
        .reduce((l, r) => l.flatMap(_ => r))
    } yield inputs
}
