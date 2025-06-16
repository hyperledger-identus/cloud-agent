package org.hyperledger.identus.verification.controller

import org.hyperledger.identus.agent.server.http.CustomServerInterceptors
import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.castor.core.model.did.VerificationRelationship
import org.hyperledger.identus.castor.core.service.MockDIDService
import org.hyperledger.identus.iam.authentication.{AuthenticatorWithAuthZ, DefaultEntityAuthenticator}
import org.hyperledger.identus.pollux.core.service.*
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.core.service.verification.{VcVerificationService, VcVerificationServiceImpl}
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.shared.models.WalletId.*
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import sttp.client3.testing.SttpBackendStub
import sttp.client3.UriContext
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.RIOMonadError
import zio.*
import zio.test.*

trait VcVerificationControllerTestTools extends PostgresTestContainerSupport {
  self: ZIOSpecDefault =>

  protected val (issuerOp, issuerKp, issuerDidMetadata, issuerDidData) =
    MockDIDService.createDID(VerificationRelationship.AssertionMethod)

  protected val issuer =
    Issuer(
      did = issuerDidData.id.did,
      signer = ES256KSigner(issuerKp.privateKey.toJavaPrivateKey),
      publicKey = issuerKp.publicKey.toJavaPublicKey
    )

  val didResolverLayer = ZLayer.fromZIO(ZIO.succeed(makeResolver(Map.empty)))

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

  protected val defaultWalletLayer = ZLayer.succeed(WalletAccessContext(WalletId.default))

  lazy val testEnvironmentLayer =
    zio.test.testEnvironment ++ ZLayer.make[
      VcVerificationController & VcVerificationService & AuthenticatorWithAuthZ[BaseEntity]
    ](
      didResolverLayer,
      ResourceUrlResolver.layer,
      VcVerificationControllerImpl.layer,
      VcVerificationServiceImpl.layer,
      DefaultEntityAuthenticator.layer
    )

  val vcVerificationUriBase = uri"http://test.com/verification/credential"

  def bootstrapOptions[F[_]](monadError: MonadError[F]): CustomiseInterceptors[F, Any] = {
    new CustomiseInterceptors[F, Any](_ => ())
      .exceptionHandler(CustomServerInterceptors.tapirExceptionHandler)
      .rejectHandler(CustomServerInterceptors.tapirRejectHandler)
      .decodeFailureHandler(CustomServerInterceptors.tapirDecodeFailureHandler)
  }

  def httpBackend(controller: VcVerificationController, authenticator: AuthenticatorWithAuthZ[BaseEntity]) = {
    val vcVerificationEndpoints = VcVerificationServerEndpoints(controller, authenticator, authenticator)
    val backend =
      TapirStubInterpreter(
        bootstrapOptions(new RIOMonadError[Any]),
        SttpBackendStub(new RIOMonadError[Any])
      )
        .whenServerEndpoint(vcVerificationEndpoints.verifyEndpoint)
        .thenRunLogic()
        .backend()
    backend
  }

}
