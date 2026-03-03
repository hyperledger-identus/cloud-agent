package org.hyperledger.identus.did.core.service

import org.hyperledger.identus.did.core.model.did.{
  DIDData,
  DIDMetadata,
  PrismDID,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError}
import zio.*

trait DIDService {
  def scheduleOperation(operation: SignedPrismDIDOperation): IO[DIDOperationError, ScheduleDIDOperationOutcome]
  def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]]
  def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]]
}
