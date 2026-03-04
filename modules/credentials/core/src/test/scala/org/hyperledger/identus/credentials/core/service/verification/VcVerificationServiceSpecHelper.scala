package org.hyperledger.identus.credentials.core.service.verification

import org.hyperledger.identus.credentials.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.credentials.vc.jwt.*
import org.hyperledger.identus.did.core.model.did.VerificationRelationship
import org.hyperledger.identus.did.core.service.{DIDService, MockDIDService}
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import org.hyperledger.identus.wallet.service.{ManagedDIDService, MockManagedDIDService}
import zio.*
import zio.mock.Expectation

trait VcVerificationServiceSpecHelper {
  protected val defaultWalletLayer: ULayer[WalletAccessContext] = ZLayer.succeed(WalletAccessContext(WalletId.default))

  protected val (issuerOp, issuerKp, issuerDidMetadata, issuerDidData) =
    MockDIDService.createDID(VerificationRelationship.AssertionMethod)

  private val testSigner = new Signer {
    override def encode(claim: zio.json.ast.Json): JWT = {
      val header =
        java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"alg":"none","typ":"JWT"}""".getBytes)
      val payload = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(claim.toString.getBytes)
      JWT(s"$header.$payload.test-signature")
    }
    override def generateProofForJson(
        payload: zio.json.ast.Json,
        pk: java.security.PublicKey
    ): zio.Task[Proof] =
      zio.ZIO.fail(Throwable("Test signer: generateProofForJson not implemented"))
  }

  protected val issuer =
    Issuer(
      did = issuerDidData.id.did,
      signer = testSigner,
      publicKey = issuerKp.publicKey.toJavaPublicKey
    )

  protected val issuerDidServiceExpectations: Expectation[DIDService] =
    MockDIDService.resolveDIDExpectation(issuerDidMetadata, issuerDidData)

  protected val issuerManagedDIDServiceExpectations: Expectation[ManagedDIDService] =
    MockManagedDIDService.getManagedDIDStateExpectation(issuerOp)
      ++ MockManagedDIDService.findDIDKeyPairExpectation(issuerKp)

  protected val emptyDidResolverLayer: ULayer[DidResolver] =
    ZLayer.succeed(
      ((didUrl: String) =>
        ZIO.succeed(
          DIDResolutionFailed(NotFound(s"DIDDocument not found for $didUrl"))
        )
      ): DidResolver
    )

  protected val vcVerificationServiceLayer: ZLayer[Any, Nothing, VcVerificationService & WalletAccessContext] =
    emptyDidResolverLayer ++ ResourceUrlResolver.layer ++ VcJwtServiceStub.layer >>>
      VcVerificationServiceImpl.layer ++ defaultWalletLayer

  protected val someVcVerificationServiceLayer: URLayer[UriResolver, VcVerificationService] =
    ZLayer.makeSome[UriResolver, VcVerificationService](
      emptyDidResolverLayer,
      VcJwtServiceStub.layer,
      VcVerificationServiceImpl.layer
    )

}
