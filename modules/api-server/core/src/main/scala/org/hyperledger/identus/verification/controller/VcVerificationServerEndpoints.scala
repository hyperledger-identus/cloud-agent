package org.hyperledger.identus.verification.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.verification.controller
import org.hyperledger.identus.verification.controller.VcVerificationEndpoints.verify
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class VcVerificationServerEndpoints(
    vcVerificationController: VcVerificationController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  val verifyEndpoint: ZServerEndpoint[Any, Any] =
    verify
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, request: List[controller.http.VcVerificationRequest]) =>
          vcVerificationController
            .verify(request)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    verifyEndpoint
  )

}

object VcVerificationServerEndpoints {
  def all: URIO[VcVerificationController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      vcVerificationController <- ZIO.service[VcVerificationController]
      vcVerificationProofEndpoints =
        new VcVerificationServerEndpoints(
          vcVerificationController,
          authenticator,
          authenticator
        )
    } yield vcVerificationProofEndpoints.all
  }
}
