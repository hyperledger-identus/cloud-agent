package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.vdr.VdrService
import org.hyperledger.identus.api.http.ErrorResponse
import zio.*

trait VdrController {
  def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]]
}

class VdrControllerImpl(service: VdrService) extends VdrController {
  override def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]] =
    service.read(vdrUrl).orDie // FIXME: not all errors are defect
}

object VdrControllerImpl {
  val layer: URLayer[VdrService, VdrController] = ZLayer.fromFunction(VdrControllerImpl(_))
}
