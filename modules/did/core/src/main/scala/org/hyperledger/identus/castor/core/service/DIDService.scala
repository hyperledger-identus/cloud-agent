package org.hyperledger.identus.castor.core.service

import org.hyperledger.identus.castor.core.model.did.{
  DIDData,
  DIDMetadata,
  PrismDID,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError}
import zio.*

trait DIDService {
  def scheduleOperation(operation: SignedPrismDIDOperation): IO[DIDOperationError, ScheduleDIDOperationOutcome]
  def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]]
  def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]]
}
