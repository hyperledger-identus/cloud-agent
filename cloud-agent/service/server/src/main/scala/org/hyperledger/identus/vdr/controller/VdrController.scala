package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.vdr.VdrService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.vdr.controller.http.CreateVdrEntryResponse
import org.hyperledger.identus.vdr.controller.http.UpdateVdrEntryResponse
import zio.*

trait VdrController {
  def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]]
  def createVdrEntry(data: Array[Byte], params: Map[String, String]): IO[ErrorResponse, CreateVdrEntryResponse]
  def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, UpdateVdrEntryResponse]
  def deleteVdrEntry(url: String, params: Map[String, String]): IO[ErrorResponse, Unit]
}

// FIXME: not all errors are defect
class VdrControllerImpl(service: VdrService) extends VdrController {

  override def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]] =
    service.read(url).orDie

  override def createVdrEntry(
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, CreateVdrEntryResponse] =
    service
      .create(data, params)
      .orDie
      .map(url => CreateVdrEntryResponse(url))

  override def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, UpdateVdrEntryResponse] =
    service
      .update(data, url, params)
      .orDie
      .map(url => UpdateVdrEntryResponse(url))

  override def deleteVdrEntry(url: String, params: Map[String, String]): IO[ErrorResponse, Unit] =
    service.delete(url, params).orDie

}

object VdrControllerImpl {
  val layer: URLayer[VdrService, VdrController] = ZLayer.fromFunction(VdrControllerImpl(_))
}
