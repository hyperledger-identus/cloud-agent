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
import zio.http.*

object NeoPrismDIDService {
  val layer: URLayer[Client, DIDService] =
    ZLayer.fromFunction(NeoPrismDIDService(_))
}

private class NeoPrismDIDService(client: Client) extends DIDService:

  private val baseClient = client.host("localhost").port(18080)

  override def scheduleOperation(
      operation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] = throw NotImplementedError()

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] = throw NotImplementedError()

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
    for
      resp <- baseClient.batched
        .addHeader("Content-Type", "application/did-resolution")
        .get(s"api/dids/$did")
        .mapError(ex => DIDResolutionError.DLTProxyError("Error resolving DID document from NeoPRISM", ex))
        .debug("resolution result")
      metadata <- resp.status match
        case Status.BadRequest => ZIO.none
        case Status.NotFound   => ZIO.none
        case _                 => ZIO.dieMessage("not implemented")
      _ <- ZIO.debug(s"metadata: $metadata")
    yield throw NotImplementedError()
