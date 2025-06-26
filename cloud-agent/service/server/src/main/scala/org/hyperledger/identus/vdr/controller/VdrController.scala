package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.vdr.VdrService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.vdr.controller.http.CreateVdrEntryResponse
import zio.*

trait VdrController {
  def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]]
  def createVdrEntry(data: Array[Byte], params: Map[String, String]): IO[ErrorResponse, CreateVdrEntryResponse]
}

// FIXME: not all errors are defect
class VdrControllerImpl(service: VdrService) extends VdrController {

  override def getVdrEntry(vdrUrl: String): IO[ErrorResponse, Array[Byte]] =
    service.read(vdrUrl).orDie

  override def createVdrEntry(
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, CreateVdrEntryResponse] = {
    service
      .create(data, params)
      .orDie
      .map(url => CreateVdrEntryResponse(url))
  }

}

object VdrControllerImpl {
  val layer: URLayer[VdrService, VdrController] = ZLayer.fromFunction(VdrControllerImpl(_))
}
