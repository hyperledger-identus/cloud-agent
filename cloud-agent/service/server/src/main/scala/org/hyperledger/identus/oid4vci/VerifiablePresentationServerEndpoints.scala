package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.oid4vci.controller.VerifiablePresentationController
import sttp.tapir.ztapir.*
import zio.*

class VerifiablePresentationServerEndpoints(
    controller: VerifiablePresentationController,
) {
  val verifyServerEndpoint: ZServerEndpoint[Any, Any] =
    VerifiablePresentationEndpoints.verifyEndpoint
      .zServerSecurityLogic(ZIO.succeed)
      .serverLogic { _ => _ => ZIO.dieMessage("not implement") }

  val responseSubmissionsServerEndpoint: ZServerEndpoint[Any, Any] =
    VerifiablePresentationEndpoints.responseSubmissionEndpoint
      .zServerLogic { case (rc, form) => controller.responseSubmission(form) }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    verifyServerEndpoint,
    responseSubmissionsServerEndpoint,
  )
}

object VerifiablePresentationServerEndpoints {
  def all: URIO[VerifiablePresentationController, List[ZServerEndpoint[Any, Any]]] =
    for {
      controller <- ZIO.service[VerifiablePresentationController]
    } yield VerifiablePresentationServerEndpoints(controller).all
}
