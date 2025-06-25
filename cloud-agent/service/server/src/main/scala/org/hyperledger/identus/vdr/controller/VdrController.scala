package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.ErrorResponse
import zio.*

trait VdrController {
  def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]]
}

// TODO: implement this ...
class VdrControllerImpl() extends VdrController {
  override def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]] = ZIO.succeed(Array[Byte](1, 2, 3, 4))
}

object VdrControllerImpl {
  val layer: ULayer[VdrController] = ZLayer.succeed(VdrControllerImpl())
}
