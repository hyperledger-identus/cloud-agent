package org.hyperledger.identus.castor.core.service

import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.castor.core.model.did.{
  DIDData,
  DIDMetadata,
  PrismDID,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
import zio.*

object NeoPrismDIDService {
  val layer: URLayer[NeoPrismClient, DIDService] =
    ZLayer.fromFunction(NeoPrismDIDService(_))
}

private class NeoPrismDIDService(client: NeoPrismClient) extends DIDService:

  override def scheduleOperation(
      operation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] = throw NotImplementedError()

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] = throw NotImplementedError()

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
    for
      // Call client to get resolution metadata (already as DIDMetadata)
      metadataOpt <- client
        .getResolutionMetadata(did)
        .mapError(ex => DIDResolutionError.DLTProxyError("Error resolving DID document from NeoPRISM", ex))

      // If DID not found, return None early
      result <- metadataOpt match
        case None => ZIO.succeed(None)
        case Some(metadata) =>
          for
            // Call client to get protobuf DIDData
            protoDIDDataOpt <- client
              .getDIDData(did)
              .mapError(ex => DIDResolutionError.DLTProxyError("Error fetching DIDData from NeoPRISM", ex))

            // Handle case where resolution succeeded but DID data is missing
            protoDIDData <- protoDIDDataOpt match
              case Some(data) => ZIO.succeed(data)
              case None =>
                ZIO.fail(
                  DIDResolutionError.UnexpectedDLTResult("DIDData not found but resolution metadata succeeded")
                )

            // Convert protobuf to domain model
            didData <- ZIO
              .fromEither(protoDIDData.toDomain)
              .mapError(e =>
                DIDResolutionError.UnexpectedDLTResult(s"Failed to convert protobuf DIDData to domain model: $e")
              )
          yield Some((metadata, didData))
    yield result
