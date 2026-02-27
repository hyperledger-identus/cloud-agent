package org.hyperledger.identus.castor.core.service

import org.hyperledger.identus.castor.core.model.did.{
  DIDData,
  DIDMetadata,
  PrismDID,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  ScheduledDIDOperationStatus,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
import org.hyperledger.identus.shared.models.HexString
import zio.*

import scala.collection.immutable.ArraySeq

object NeoPrismDIDService {
  val layer: URLayer[NeoPrismClient, DIDService] =
    ZLayer.fromFunction(NeoPrismDIDService(_))
}

private class NeoPrismDIDService(client: NeoPrismClient) extends DIDService:

  override def scheduleOperation(
      operation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] =
    for
      operationId <- client
        .submitSignedOperation(operation)
        .mapError(ex => DIDOperationError.DLTProxyError("Error submitting operation to NeoPRISM", ex))
      operationIdBytes <- ZIO
        .fromTry(HexString.fromString(operationId))
        .mapError(_ => DIDOperationError.UnexpectedDLTResult(s"Invalid operation ID format: $operationId"))
        .map(_.toByteArray)
      outcome = ScheduleDIDOperationOutcome(
        did = operation.operation.did,
        operation = operation.operation,
        operationId = ArraySeq.unsafeWrapArray(operationIdBytes)
      )
    yield outcome

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] =
    val operationIdHex = HexString.fromByteArray(operationId).toString
    for operationFound <- client
        .isOperationIndexed(operationIdHex)
        .mapError(ex => DIDOperationError.DLTProxyError("Error checking operation status from NeoPRISM", ex))
    yield
      if operationFound then Some(ScheduledDIDOperationDetail(status = ScheduledDIDOperationStatus.Confirmed))
      else Some(ScheduledDIDOperationDetail(status = ScheduledDIDOperationStatus.AwaitingConfirmation))

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
    for
      metadataOpt <- client
        .getResolutionMetadata(did)
        .mapError(ex => DIDResolutionError.DLTProxyError("Error resolving DID document from NeoPRISM", ex))
      protoDIDDataOpt <- client
        .getDIDData(did)
        .mapError(ex => DIDResolutionError.DLTProxyError("Error fetching DIDData from NeoPRISM", ex))
      didDataOpt = protoDIDDataOpt.flatMap(_.toDomain.toOption)
    yield metadataOpt.zip(didDataOpt)
