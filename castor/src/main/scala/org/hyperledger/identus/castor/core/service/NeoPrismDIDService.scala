package org.hyperledger.identus.castor.core.service

import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.castor.core.model.did.{
  CanonicalPrismDID,
  DIDData,
  DIDMetadata,
  PrismDID,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
import org.hyperledger.identus.shared.models.HexString
import zio.*
import zio.http.*
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}

import java.time.Instant
import scala.collection.immutable.ArraySeq

object NeoPrismDIDService {
  val layer: URLayer[Client, DIDService] =
    ZLayer.fromFunction(NeoPrismDIDService(_))

  // JSON response models from NeoPRISM API
  private case class NeoPrismResolutionResult(
      didResolutionMetadata: NeoPrismResolutionMetadata,
      didDocumentMetadata: NeoPrismDocumentMetadata,
      didDocument: Option[NeoPrismDidDocument]
  )

  private object NeoPrismResolutionResult {
    given decoder: JsonDecoder[NeoPrismResolutionResult] = DeriveJsonDecoder.gen[NeoPrismResolutionResult]
  }

  private case class NeoPrismResolutionMetadata(
      contentType: Option[String],
      error: Option[NeoPrismResolutionError]
  )

  private object NeoPrismResolutionMetadata {
    given decoder: JsonDecoder[NeoPrismResolutionMetadata] = DeriveJsonDecoder.gen[NeoPrismResolutionMetadata]
  }

  private case class NeoPrismResolutionError(
      `type`: String,
      title: Option[String],
      detail: Option[String]
  )

  private object NeoPrismResolutionError {
    given decoder: JsonDecoder[NeoPrismResolutionError] = DeriveJsonDecoder.gen[NeoPrismResolutionError]
  }

  private case class NeoPrismDocumentMetadata(
      canonicalId: Option[String],
      created: Option[String],
      deactivated: Option[Boolean],
      updated: Option[String],
      versionId: Option[String]
  )

  private object NeoPrismDocumentMetadata {
    given decoder: JsonDecoder[NeoPrismDocumentMetadata] = DeriveJsonDecoder.gen[NeoPrismDocumentMetadata]
  }

  private case class NeoPrismDidDocument(
      id: String
  )

  private object NeoPrismDidDocument {
    given decoder: JsonDecoder[NeoPrismDidDocument] = DeriveJsonDecoder.gen[NeoPrismDidDocument]
  }
}

private class NeoPrismDIDService(client: Client) extends DIDService:
  import NeoPrismDIDService.*

  private val baseClient = client.host("localhost").port(18080)

  override def scheduleOperation(
      operation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] = throw NotImplementedError()

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] = throw NotImplementedError()

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
    for
      // Step 1: Call GET /api/dids/{did} to get resolution metadata
      resp <- baseClient.batched
        .addHeader("Content-Type", "application/did-resolution")
        .get(s"api/dids/$did")
        .mapError(ex => DIDResolutionError.DLTProxyError("Error resolving DID document from NeoPRISM", ex))

      // Step 2: Handle response status codes
      resolutionResultOpt <- resp.status match
        case Status.Ok =>
          resp.body.asString
            .mapError(t => DIDResolutionError.DLTProxyError("Failed to parse response body", t))
            .flatMap { body =>
              ZIO
                .fromEither(body.fromJson[NeoPrismResolutionResult])
                .mapError(e => DIDResolutionError.DLTProxyError(s"Failed to decode JSON: $e", null))
                .map(Some(_))
            }
        case Status.NotFound | Status.BadRequest =>
          ZIO.succeed(None)
        case status =>
          ZIO.fail(DIDResolutionError.DLTProxyError(s"Unexpected status code: ${status.code}", null))

      // Step 3: If DID not found, return None early
      result <- resolutionResultOpt match
        case None => ZIO.succeed(None)
        case Some(resolutionResult) =>
          for
            // Step 4: Call GET /api/did-data/{did} to get protobuf DIDData
            didDataResp <- baseClient.batched
              .get(s"api/did-data/$did")
              .mapError(ex => DIDResolutionError.DLTProxyError("Error fetching DIDData from NeoPRISM", ex))

            // Step 5: Parse hex-encoded protobuf
            protoDIDData: node_models.DIDData <- didDataResp.status match
              case Status.Ok =>
                for
                  hexStr <- didDataResp.body.asString
                    .mapError(t => DIDResolutionError.DLTProxyError("Failed to read DIDData response body", t))
                  hexBytes <- ZIO
                    .fromTry(HexString.fromString(hexStr))
                    .mapError(e => DIDResolutionError.DLTProxyError(s"Invalid hex string: ${e.getMessage}", e))
                  protoData <- ZIO
                    .attempt(node_models.DIDData.parseFrom(hexBytes.toByteArray))
                    .mapError(t => DIDResolutionError.DLTProxyError("Failed to parse protobuf", t))
                yield protoData
              case Status.NotFound =>
                ZIO.fail(
                  DIDResolutionError.UnexpectedDLTResult("DIDData not found but resolution metadata succeeded")
                )
              case status =>
                ZIO.fail(
                  DIDResolutionError.DLTProxyError(s"Unexpected status code from did-data: ${status.code}", null)
                )

            // Step 6: Convert protobuf to domain model
            didData <- ZIO
              .fromEither(protoDIDData.toDomain)
              .mapError(e =>
                DIDResolutionError.UnexpectedDLTResult(s"Failed to convert protobuf DIDData to domain model: $e")
              )

            // Step 7: Convert NeoPRISM metadata to domain model
            metadata = convertToMetadata(resolutionResult.didDocumentMetadata)
          yield Some((metadata, didData))
    yield result

  private def convertToMetadata(documentMetadata: NeoPrismDocumentMetadata): DIDMetadata = {
    val lastOperationHash = documentMetadata.versionId
      .flatMap(v => HexString.fromString(v).toOption)
      .map(h => ArraySeq.unsafeWrapArray(h.toByteArray))
      .getOrElse(ArraySeq.empty[Byte])

    val canonicalId: Option[CanonicalPrismDID] = documentMetadata.canonicalId.flatMap { cidStr =>
      PrismDID.fromString(cidStr).toOption.map(_.asCanonical)
    }

    val created: Option[Instant] = documentMetadata.created.flatMap { createdStr =>
      scala.util.Try(Instant.parse(createdStr)).toOption
    }

    val updated: Option[Instant] = documentMetadata.updated.flatMap { updatedStr =>
      scala.util.Try(Instant.parse(updatedStr)).toOption
    }

    DIDMetadata(
      lastOperationHash = lastOperationHash,
      canonicalId = canonicalId,
      deactivated = documentMetadata.deactivated.getOrElse(false),
      created = created,
      updated = updated
    )
  }
