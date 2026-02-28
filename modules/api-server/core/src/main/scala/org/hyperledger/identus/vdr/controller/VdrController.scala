package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.vdr.VdrService
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.vdr.controller.http.{
  CreateVdrEntryResponse,
  DeleteVdrEntryResponse,
  Proof,
  UpdateVdrEntryResponse,
  VdrOperationStatusResponse
}
import zio.*

import scala.language.implicitConversions

trait VdrController {
  def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]]
  def createVdrEntry(
      data: Array[Byte],
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, CreateVdrEntryResponse]
  def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, UpdateVdrEntryResponse]
  def deleteVdrEntry(
      url: String,
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, DeleteVdrEntryResponse]
  def entryProof(url: String): IO[ErrorResponse, Proof]
  def operationStatus(operationId: String): IO[ErrorResponse, VdrOperationStatusResponse]
}

class VdrControllerImpl(service: VdrService) extends VdrController {

  override def getVdrEntry(url: String): IO[ErrorResponse, Array[Byte]] =
    service.read(url)

  override def createVdrEntry(
      data: Array[Byte],
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, CreateVdrEntryResponse] =
    service
      .create(data, params, didKeyId)
      .map(res => CreateVdrEntryResponse(res.url, res.operationId))

  override def updateVdrEntry(
      url: String,
      data: Array[Byte],
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, UpdateVdrEntryResponse] =
    service
      .update(data, url, params, didKeyId)
      .map(res => UpdateVdrEntryResponse(res.map(_.url), res.flatMap(_.operationId)))

  override def deleteVdrEntry(
      url: String,
      params: Map[String, String],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, ErrorResponse, DeleteVdrEntryResponse] =
    service.delete(url, params, didKeyId).map(DeleteVdrEntryResponse(_))

  override def entryProof(url: String): IO[ErrorResponse, Proof] =
    service.verify(url, false).map(identity)

  override def operationStatus(operationId: String): IO[ErrorResponse, VdrOperationStatusResponse] =
    service
      .getOperationStatus(operationId)
      .map(status => VdrOperationStatusResponse(status.status, status.details, status.transactionId))
}

object VdrControllerImpl {
  val layer: URLayer[VdrService, VdrController] = ZLayer.fromFunction(VdrControllerImpl(_))
}
