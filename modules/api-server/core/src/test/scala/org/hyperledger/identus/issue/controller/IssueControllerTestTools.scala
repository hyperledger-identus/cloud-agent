package org.hyperledger.identus.issue.controller

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.typesafe.config.ConfigFactory
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.connections.core.service.ConnectionService
import org.hyperledger.identus.credentials.core.model.IssueCredentialRecord
import org.hyperledger.identus.credentials.core.service.*
import org.hyperledger.identus.credentials.vc.jwt.*
import org.hyperledger.identus.did.core.service.DIDService
import org.hyperledger.identus.iam.authentication.{AuthenticatorWithAuthZ, DefaultEntityAuthenticator}
import org.hyperledger.identus.issue.controller.http.IssueCredentialRecordPage
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.server.http.CustomServerInterceptors
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.wallet.model.BaseEntity
import org.hyperledger.identus.wallet.service.ManagedDIDService
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

  private val issueControllerConfigLayer = ZLayer.fromFunction((cfg: AppConfig) =>
    IssueControllerConfig(
      defaultJwtVCOfferDomain = cfg.credentials.defaultJwtVCOfferDomain,
      httpEndpointServiceName = cfg.agent.httpEndpoint.serviceName,
      httpEndpointPublicUrl = cfg.agent.httpEndpoint.publicEndpointUrl,
      issuanceInvitationExpiry = cfg.credentials.issuanceInvitationExpiry,
      didCommEndpointUrl = cfg.agent.didCommEndpoint.publicEndpointUrl,
      featureFlag = cfg.featureFlag,
    )
  )

  lazy val testEnvironmentLayer =
    ZLayer.makeSome[
      ManagedDIDService & DIDService & CredentialService & CredentialDefinitionService & ConnectionService,
      IssueController & AppConfig & PostgreSQLContainer & AuthenticatorWithAuthZ[BaseEntity]
    ](
      IssueControllerImpl.layer,
      issueControllerConfigLayer,
      configLayer,
      pgContainerLayer,
      DefaultEntityAuthenticator.layer
    )

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
