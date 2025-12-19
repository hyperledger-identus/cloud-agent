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

object NeoPrismDIDService {
  val layer: URLayer[Any, DIDService] =
    ZLayer.succeed(NeoPrismDIDService())
}

private class NeoPrismDIDService() extends DIDService {

  override def scheduleOperation(
      operation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] = throw NotImplementedError()

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] = throw NotImplementedError()

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
    throw NotImplementedError()

}
