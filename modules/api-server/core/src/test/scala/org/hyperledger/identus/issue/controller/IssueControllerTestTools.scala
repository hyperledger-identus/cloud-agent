package org.hyperledger.identus.issue.controller

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.typesafe.config.ConfigFactory
import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.agent.server.http.CustomServerInterceptors
import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.iam.authentication.{AuthenticatorWithAuthZ, DefaultEntityAuthenticator}
import org.hyperledger.identus.issue.controller.http.IssueCredentialRecordPage
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord
import org.hyperledger.identus.pollux.core.service.*
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import sttp.client3.{DeserializationException, Response, UriContext}
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.RIOMonadError
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*

trait IssueControllerTestTools extends PostgresTestContainerSupport {
  self: ZIOSpecDefault =>

  type IssueCredentialBadRequestResponse =
    Response[Either[DeserializationException[String], ErrorResponse]]
  type IssueCredentialResponse =
    Response[Either[DeserializationException[String], IssueCredentialRecord]]
  type IssueCredentialPageResponse =
    Response[
      Either[DeserializationException[String], IssueCredentialRecordPage]
    ]
  val didResolverLayer = ZLayer.fromZIO(ZIO.succeed(makeResolver(Map.empty)))

  val configLayer = ZLayer.fromZIO(
    TypesafeConfigProvider
      .fromTypesafeConfig(ConfigFactory.load())
      .load(AppConfig.config)
  )

  private def makeResolver(lookup: Map[String, DIDDocument]): DidResolver = (didUrl: String) => {
    lookup
      .get(didUrl)
      .fold(
        ZIO.succeed(DIDResolutionFailed(NotFound(s"DIDDocument not found for $didUrl")))
      )((didDocument: DIDDocument) => {
        ZIO.succeed(
          DIDResolutionSucceeded(
            didDocument,
            DIDDocumentMetadata()
          )
        )
      })
  }

  lazy val testEnvironmentLayer =
    ZLayer.makeSome[
      ManagedDIDService & DIDService & CredentialService & CredentialDefinitionService & ConnectionService,
      IssueController & AppConfig & PostgreSQLContainer & AuthenticatorWithAuthZ[BaseEntity]
    ](IssueControllerImpl.layer, configLayer, pgContainerLayer, DefaultEntityAuthenticator.layer)

  val issueUriBase = uri"http://test.com/issue-credentials/"

  def bootstrapOptions[F[_]](monadError: MonadError[F]): CustomiseInterceptors[F, Any] = {
    new CustomiseInterceptors[F, Any](_ => ())
      .exceptionHandler(CustomServerInterceptors.tapirExceptionHandler)
      .rejectHandler(CustomServerInterceptors.tapirRejectHandler)
      .decodeFailureHandler(CustomServerInterceptors.tapirDecodeFailureHandler)
  }

  def httpBackend(controller: IssueController, authenticator: AuthenticatorWithAuthZ[BaseEntity]) = {
    val issueEndpoints = IssueServerEndpoints(controller, authenticator, authenticator)

    val backend =
      TapirStubInterpreter(
        bootstrapOptions(new RIOMonadError[Any]),
        SttpBackendStub(new RIOMonadError[Any])
      )
        .whenServerEndpoint(issueEndpoints.createCredentialOfferEndpoint)
        .thenRunLogic()
        .whenServerEndpoint(issueEndpoints.getCredentialRecordsEndpoint)
        .thenRunLogic()
        .whenServerEndpoint(issueEndpoints.getCredentialRecordEndpoint)
        .thenRunLogic()
        .whenServerEndpoint(issueEndpoints.acceptCredentialOfferEndpoint)
        .thenRunLogic()
        .whenServerEndpoint(issueEndpoints.issueCredentialEndpoint)
        .thenRunLogic()
        .backend()
    backend
  }
}
