package org.hyperledger.identus.agent.vdr

import interfaces.Proof
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{IO, UIO, ZIO}

type VdrUrl = String
type VdrOptions = Map[String, String]
final case class VdrOperationResult(url: VdrUrl, operationId: Option[String])
final case class VdrOperationStatus(status: String, details: Option[String], transactionId: Option[String])

trait VdrService {
  def identifier: String
  def version: String

  def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.DriverNotFound | VdrServiceError.MissingVdrKey, VdrOperationResult]
  def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey,
    Option[VdrOperationResult]
  ]
  def read(url: VdrUrl): IO[VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound, Array[Byte]]
  def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound | VdrServiceError.MissingVdrKey,
    Option[String]
  ]
  def verify(url: VdrUrl, returnData: Boolean = false): UIO[Proof]

  def getOperationStatus(operationId: String): IO[VdrServiceError.DriverNotFound, VdrOperationStatus]
}
