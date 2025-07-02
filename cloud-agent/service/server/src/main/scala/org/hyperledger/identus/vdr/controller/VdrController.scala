package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.vdr.VdrService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.vdr.controller.http.{CreateVdrEntryResponse, Proof, UpdateVdrEntryResponse}
import zio.*

import scala.language.implicitConversions

trait VdrController {
  def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]]
  def createVdrEntry(data: Array[Byte], params: Map[String, String]): IO[ErrorResponse, CreateVdrEntryResponse]
  def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, UpdateVdrEntryResponse]
  def deleteVdrEntry(url: String, params: Map[String, String]): IO[ErrorResponse, Unit]
  def entryProof(url: String): IO[ErrorResponse, Proof]
}

class VdrControllerImpl(service: VdrService) extends VdrController {

  override def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]] =
    service.read(url)

  override def createVdrEntry(
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, CreateVdrEntryResponse] =
    service
      .create(data, params)
      .map(url => CreateVdrEntryResponse(url))

  override def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String]
  ): IO[ErrorResponse, UpdateVdrEntryResponse] =
    service
      .update(data, url, params)
      .map(url => UpdateVdrEntryResponse(url))

  override def deleteVdrEntry(url: String, params: Map[String, String]): IO[ErrorResponse, Unit] =
    service.delete(url, params)

  override def entryProof(url: String): IO[ErrorResponse, Proof] =
    service.verify(url, false).map(identity)
}

object VdrControllerImpl {
  val layer: URLayer[VdrService, VdrController] = ZLayer.fromFunction(VdrControllerImpl(_))
}
