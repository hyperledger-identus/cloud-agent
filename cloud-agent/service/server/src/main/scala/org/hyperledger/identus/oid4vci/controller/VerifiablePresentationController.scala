package org.hyperledger.identus.oid4vci.controller

import org.hyperledger.identus.oid4vci.http.VerifiablePresentationSubmissionForm
import org.hyperledger.identus.pollux.core.service.OIDC4VPService
import org.hyperledger.identus.pollux.prex.*
import zio.*
import zio.json.*

import scala.language.implicitConversions

trait VerifiablePresentationController {
  def responseSubmission(submissionForm: VerifiablePresentationSubmissionForm): UIO[Unit]
}

class VerifiablePresentationControllerImpl(service: OIDC4VPService) extends VerifiablePresentationController {

  override def responseSubmission(submissionForm: VerifiablePresentationSubmissionForm): UIO[Unit] =
    for {
      ps <- ZIO
        .fromEither(submissionForm.presentation_submission.fromJson[PresentationSubmission])
        .orDieWith(Exception(_))
      _ <- service.verifyJwt(ps, submissionForm.vp_token)
    } yield ()
}

object VerifiablePresentationControllerImpl {
  def layer: URLayer[OIDC4VPService, VerifiablePresentationController] =
    ZLayer.fromFunction(VerifiablePresentationControllerImpl(_))
}
