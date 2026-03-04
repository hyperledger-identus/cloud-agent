package org.hyperledger.identus.castor.core.service

import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.castor.core.model.did.{CanonicalPrismDID, DIDMetadata, PrismDID, SignedPrismDIDOperation}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
import org.hyperledger.identus.shared.models.HexString
import zio.*
import zio.http.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

import java.time.Instant
import scala.collection.immutable.ArraySeq
import scala.util.Try

case class NeoPrismConfig(baseUrl: URL)

/** Result of submitting signed operations to neoprism REST API. */
final case class NeoPrismSubmissionResult(
    txId: String,
    operationIds: Seq[String]
)

/** Metadata about a VDR entry from neoprism. */
final case class NeoPrismVdrEntryMetadata(
    entryHash: String,
    latestEventHash: String,
    status: String
)

trait NeoPrismClient {

  /** Get resolution metadata for a DID.
    * @return
    *   None if DID not found (404), Some if found, throws on other errors
    */
  def getResolutionMetadata(did: PrismDID): Task[Option[DIDMetadata]]

  /** Get DID data as protobuf.
    * @return
    *   None if DID data not found (404), Some if found, throws on other errors
    */
  def getDIDData(did: PrismDID): Task[Option[node_models.DIDData]]

  /** Submit a signed operation to NeoPRISM.
    * @param signedOperation
    *   Signed Prism DID operation to submit
    * @return
    *   Transaction ID (hex string)
    */
  def submitSignedOperation(signedOperation: SignedPrismDIDOperation): Task[String]

  /** Check if an operation exists on the NeoPRISM node.
    * @param operationId
    *   Operation ID (hex string)
    * @return
    *   true if operation found (200), false if not found (404), throws on errors (400/500)
    */
  def isOperationIndexed(operationId: String): Task[Boolean]

  /** Submit hex-encoded signed operations for VDR entries.
    * @param hexEncodedOps
    *   Sequence of hex-encoded SignedAtalaOperation protobuf bytes
    * @return
    *   Submission result with txId and operationIds
    */
  def submitVdrOperation(hexEncodedOps: Seq[String]): Task[NeoPrismSubmissionResult]

  /** Get raw VDR blob data by entry hash.
    * @param entryHash
    *   Hex string of the entry hash
    * @return
    *   None if not found (404), Some(bytes) if found
    */
  def getVdrBlob(entryHash: String): Task[Option[Array[Byte]]]

  /** Get VDR entry metadata (latest event hash, status) by entry hash. Requires neoprism endpoint: GET
    * /api/vdr-entries/{entry_hash}
    * @param entryHash
    *   Hex string of the entry hash
    * @return
    *   None if not found (404), Some(metadata) if found
    */
  def getVdrEntryMetadata(entryHash: String): Task[Option[NeoPrismVdrEntryMetadata]]
}

private class NeoPrismClientImpl(client: Client, config: NeoPrismConfig) extends NeoPrismClient {
  import NeoPrismClientImpl.*

  private val baseClient = client.url(config.baseUrl)

  override def getResolutionMetadata(did: PrismDID): Task[Option[DIDMetadata]] =
    for
      resp <- baseClient.batched
        .addHeader("Accept", "application/did-resolution")
        .get(s"api/dids/$did")
      metadataOpt <- resp.status match
        case Status.NotFound => ZIO.none
        case _               =>
          resp.body.asString
            .flatMap { body =>
              ZIO
                .fromEither(body.fromJson[NeoPrismResolutionResult])
                .mapError(e => new RuntimeException(s"Failed to decode JSON: $e"))
                .map(result => Some(convertToMetadata(result.didDocumentMetadata)))
            }
    yield metadataOpt

  override def getDIDData(did: PrismDID): Task[Option[node_models.DIDData]] =
    for
      didDataResp <- baseClient.batched
        .get(s"api/dids/$did/protobuf")
      protoDIDDataOpt <- didDataResp.status match
        case Status.Ok =>
          for
            hexStr <- didDataResp.body.asString
            hexBytes <- ZIO.fromTry(HexString.fromString(hexStr))
            protoData <- ZIO.attempt(node_models.DIDData.parseFrom(hexBytes.toByteArray))
          yield Some(protoData)
        case Status.NotFound =>
          ZIO.succeed(None)
        case status =>
          ZIO.fail(new RuntimeException(s"Unexpected status code from did-data: ${status.code}"))
    yield protoDIDDataOpt

  override def submitSignedOperation(signedOperation: SignedPrismDIDOperation): Task[String] =
    val hexString = HexString.fromByteArray(signedOperation.toSignedAtalaOperation.toByteArray).toString
    submitVdrOperation(Seq(hexString)).flatMap { result =>
      result.operationIds.headOption match
        case Some(opId) => ZIO.succeed(opId)
        case None       => ZIO.fail(new RuntimeException("No operation_id returned from NeoPRISM"))
    }

  override def isOperationIndexed(operationId: String): Task[Boolean] =
    for
      resp <- baseClient.batched.get(s"api/operations/$operationId")
      found <- resp.status match
        case Status.Ok         => ZIO.succeed(true)
        case Status.NotFound   => ZIO.succeed(false)
        case Status.BadRequest =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Invalid operation ID: $body"))
          }
        case status =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Unexpected status code ${status.code}: $body"))
          }
    yield found

  override def submitVdrOperation(hexEncodedOps: Seq[String]): Task[NeoPrismSubmissionResult] =
    val requestBody = SignedOperationSubmissionRequest(hexEncodedOps)
    for
      resp <- baseClient.batched
        .request(
          Request(
            method = Method.POST,
            url = URL.root / "api" / "submissions" / "signed-operations",
            headers = Headers(Header.ContentType(MediaType.application.json)),
            body = Body.fromString(requestBody.toJson)
          )
        )
      result <- resp.status match
        case Status.Ok =>
          resp.body.asString
            .flatMap { body =>
              ZIO
                .fromEither(body.fromJson[SignedOperationSubmissionResponse])
                .mapError(e => new RuntimeException(s"Failed to decode JSON response: $e"))
                .map(r => NeoPrismSubmissionResult(r.tx_id, r.operation_ids))
            }
        case Status.BadRequest =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Bad request submitting VDR operation: $body"))
          }
        case status =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Unexpected status code ${status.code} submitting VDR operation: $body"))
          }
    yield result

  override def getVdrBlob(entryHash: String): Task[Option[Array[Byte]]] =
    for
      resp <- baseClient.batched.get(s"api/vdr-data/$entryHash")
      dataOpt <- resp.status match
        case Status.Ok =>
          resp.body.asArray.map(Some(_))
        case Status.NotFound =>
          ZIO.succeed(None)
        case status =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Unexpected status code ${status.code} from vdr-data: $body"))
          }
    yield dataOpt

  override def getVdrEntryMetadata(entryHash: String): Task[Option[NeoPrismVdrEntryMetadata]] =
    for
      resp <- baseClient.batched.get(s"api/vdr-entries/$entryHash")
      metadataOpt <- resp.status match
        case Status.Ok =>
          resp.body.asString
            .flatMap { body =>
              ZIO
                .fromEither(body.fromJson[VdrEntryMetadataResponse])
                .mapError(e => new RuntimeException(s"Failed to decode VDR entry metadata: $e"))
                .map(r =>
                  Some(
                    NeoPrismVdrEntryMetadata(
                      entryHash = r.entry_hash,
                      latestEventHash = r.latest_event_hash,
                      status = r.status
                    )
                  )
                )
            }
        case Status.NotFound =>
          ZIO.succeed(None)
        case status =>
          resp.body.asString.flatMap { body =>
            ZIO.fail(new RuntimeException(s"Unexpected status code ${status.code} from vdr-entries: $body"))
          }
    yield metadataOpt

  private def convertToMetadata(documentMetadata: NeoPrismDocumentMetadata): DIDMetadata = {
    val lastOperationHash = documentMetadata.versionId
      .flatMap(v => HexString.fromString(v).toOption)
      .map(h => ArraySeq.unsafeWrapArray(h.toByteArray))
      .getOrElse(ArraySeq.empty[Byte])

    val canonicalId: Option[CanonicalPrismDID] = documentMetadata.canonicalId.flatMap { cidStr =>
      PrismDID.fromString(cidStr).toOption.map(_.asCanonical)
    }

    val created: Option[Instant] = documentMetadata.created.flatMap { createdStr =>
      Try(Instant.parse(createdStr)).toOption
    }

    val updated: Option[Instant] = documentMetadata.updated.flatMap { updatedStr =>
      Try(Instant.parse(updatedStr)).toOption
    }

    DIDMetadata(
      lastOperationHash = lastOperationHash,
      canonicalId = canonicalId,
      deactivated = documentMetadata.deactivated.getOrElse(false),
      created = created,
      updated = updated
    )
  }
}

object NeoPrismClientImpl {
  val layer: URLayer[Client & NeoPrismConfig, NeoPrismClient] =
    ZLayer.fromFunction((client: Client, config: NeoPrismConfig) => NeoPrismClientImpl(client, config))

  // JSON response models from NeoPRISM API - private to implementation
  private case class NeoPrismResolutionResult(
      didResolutionMetadata: NeoPrismResolutionMetadata,
      didDocumentMetadata: NeoPrismDocumentMetadata,
      didDocument: Option[NeoPrismDidDocument]
  )

  private object NeoPrismResolutionResult {
    given decoder: JsonDecoder[NeoPrismResolutionResult] = DeriveJsonDecoder.gen
  }

  private case class NeoPrismResolutionMetadata(
      contentType: Option[String],
      error: Option[NeoPrismResolutionError]
  )

  private object NeoPrismResolutionMetadata {
    given decoder: JsonDecoder[NeoPrismResolutionMetadata] = DeriveJsonDecoder.gen
  }

  private case class NeoPrismResolutionError(
      `type`: String,
      title: Option[String],
      detail: Option[String]
  )

  private object NeoPrismResolutionError {
    given decoder: JsonDecoder[NeoPrismResolutionError] = DeriveJsonDecoder.gen
  }

  private case class NeoPrismDocumentMetadata(
      canonicalId: Option[String],
      created: Option[String],
      deactivated: Option[Boolean],
      updated: Option[String],
      versionId: Option[String]
  )

  private object NeoPrismDocumentMetadata {
    given decoder: JsonDecoder[NeoPrismDocumentMetadata] = DeriveJsonDecoder.gen
  }

  private case class NeoPrismDidDocument(
      id: String
  )

  private object NeoPrismDidDocument {
    given decoder: JsonDecoder[NeoPrismDidDocument] = DeriveJsonDecoder.gen
  }

  // Request/Response models for submission endpoint
  private case class SignedOperationSubmissionRequest(signed_operations: Seq[String])

  private object SignedOperationSubmissionRequest {
    given encoder: JsonEncoder[SignedOperationSubmissionRequest] = DeriveJsonEncoder.gen
  }

  private case class SignedOperationSubmissionResponse(
      tx_id: String,
      operation_ids: Seq[String]
  )

  private object SignedOperationSubmissionResponse {
    given decoder: JsonDecoder[SignedOperationSubmissionResponse] = DeriveJsonDecoder.gen
  }

  private case class VdrEntryMetadataResponse(
      entry_hash: String,
      latest_event_hash: String,
      status: String
  )

  private object VdrEntryMetadataResponse {
    given decoder: JsonDecoder[VdrEntryMetadataResponse] = DeriveJsonDecoder.gen
  }
}
