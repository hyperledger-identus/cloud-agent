package org.hyperledger.identus.oid4vci.controller

import zio.*

trait VerifiablePresentationController {
  def responseSubmission: UIO[Unit]
}

class VerifiablePresentationControllerImpl() extends VerifiablePresentationController {
  override def responseSubmission: UIO[Unit] = ZIO.dieMessage("not implemented")
}

object VerifiablePresentationControllerImpl {
  def layer: ULayer[VerifiablePresentationController] = ZLayer.succeed(VerifiablePresentationControllerImpl())
}
