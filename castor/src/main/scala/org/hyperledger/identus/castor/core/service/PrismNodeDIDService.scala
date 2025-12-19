package org.hyperledger.identus.castor.core.service

import io.iohk.atala.prism.protos.{node_api, node_models}
import io.iohk.atala.prism.protos.node_api.NodeServiceGrpc.NodeService
import io.iohk.atala.prism.protos.node_models.OperationOutput.OperationMaybe
import org.hyperledger.identus.castor.core.model.did.{
  CanonicalPrismDID,
  DIDData,
  DIDMetadata,
  InternalPublicKey,
  LongFormPrismDID,
  PrismDID,
  PublicKey,
  ScheduleDIDOperationOutcome,
  ScheduledDIDOperationDetail,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError, OperationValidationError}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper
import org.hyperledger.identus.castor.core.util.DIDOperationValidator
import org.hyperledger.identus.shared.models.HexString
import zio.*

import java.time.Instant
import scala.collection.immutable.ArraySeq

object PrismNodeDIDService {
  val layer: URLayer[NodeService & DIDOperationValidator, DIDService] =
    ZLayer.fromFunction(PrismNodeDIDService(_, _))
}

private class PrismNodeDIDService(didOpValidator: DIDOperationValidator, nodeClient: NodeService)
    extends DIDService,
      ProtoModelHelper {

  override def scheduleOperation(
      signedOperation: SignedPrismDIDOperation
  ): IO[DIDOperationError, ScheduleDIDOperationOutcome] = {
    val operationRequest = node_api.ScheduleOperationsRequest(signedOperations = Seq(signedOperation.toProto))
    for {
      _ <- ZIO
        .fromEither(didOpValidator.validate(signedOperation.operation))
        .mapError(DIDOperationError.ValidationError.apply)
      operationOutput <- ZIO
        .fromFuture(_ => nodeClient.scheduleOperations(operationRequest))
        .mapBoth(ex => DIDOperationError.DLTProxyError("Error scheduling Node operation", ex), _.outputs.toList)
        .map {
          case output :: Nil => Right(output)
          case _ => Left(DIDOperationError.UnexpectedDLTResult("operation result is expected to have exactly 1 output"))
        }
        .absolve
      operationId <- ZIO.fromEither {
        operationOutput.operationMaybe match {
          case OperationMaybe.OperationId(id) => Right(id.toByteArray)
          case OperationMaybe.Empty =>
            Left(DIDOperationError.UnexpectedDLTResult("operation result does not contain operation detail"))
          case OperationMaybe.Error(e) =>
            Left(DIDOperationError.UnexpectedDLTResult(s"operation result was not successful: $e"))
        }
      }
    } yield ScheduleDIDOperationOutcome(
      did = signedOperation.operation.did,
      operation = signedOperation.operation,
      operationId = ArraySeq.from(operationId)
    )
  }

  override def getScheduledDIDOperationDetail(
      operationId: Array[Byte]
  ): IO[DIDOperationError, Option[ScheduledDIDOperationDetail]] = {
    for {
      result <- ZIO
        .fromFuture(_ => nodeClient.getOperationInfo(node_api.GetOperationInfoRequest(operationId.toProto)))
        .mapError(ex => DIDOperationError.DLTProxyError("Error getting Node operation information", ex))
      detail <- ZIO
        .fromEither(result.toDomain)
        .mapError(DIDOperationError.UnexpectedDLTResult.apply)
    } yield detail
  }

  override def resolveDID(did: PrismDID): IO[DIDResolutionError, Option[(DIDMetadata, DIDData)]] = {
    val canonicalDID = did.asCanonical
    val request = node_api.GetDidDocumentRequest(did = canonicalDID.toString)
    for {
      unpublishedDidData <- did match {
        case _: CanonicalPrismDID => ZIO.none
        case d: LongFormPrismDID  => extractUnpublishedDIDData(d).asSome
      }
      result <- ZIO
        .fromFuture(_ => nodeClient.getDidDocument(request))
        .mapError(ex => DIDResolutionError.DLTProxyError("Error resolving DID document from Node", ex))
      publishedDidData <- ZIO
        .fromOption(result.document)
        .foldZIO(
          _ => ZIO.none,
          didDataProto =>
            didDataProto.filterRevokedKeysAndServices
              .flatMap(didData => ZIO.fromEither(didData.toDomain))
              .mapError(DIDResolutionError.UnexpectedDLTResult.apply)
              .map { didData =>
                val (created, updated) = getMinMaxLedgerTime(didDataProto)
                val metadata = DIDMetadata(
                  lastOperationHash = ArraySeq.from(result.lastUpdateOperation.toByteArray),
                  canonicalId =
                    unpublishedDidData.map(_ => canonicalDID), // only shows canonicalId if long-form and published
                  deactivated = didData.internalKeys.isEmpty && didData.publicKeys.isEmpty,
                  created = created,
                  updated = updated
                )
                metadata -> didData
              }
              .asSome
        )
    } yield publishedDidData.orElse(unpublishedDidData)
  }

  // FIXME: This doesn't play well detecting timestamp context and revoked service due to
  // the response from Node missing the ledger data for those items.
  private def getMinMaxLedgerTime(didData: node_models.DIDData): (Option[Instant], Option[Instant]) = {
    val ledgerTimes = didData.publicKeys.flatMap(_.addedOn) ++
      didData.publicKeys.flatMap(_.revokedOn) ++
      didData.services.flatMap(_.addedOn) ++
      didData.services.flatMap(_.deletedOn)
    val instants = ledgerTimes.flatMap(_.toInstant)
    (instants.minOption, instants.maxOption)
  }

  private def extractUnpublishedDIDData(did: LongFormPrismDID): IO[DIDResolutionError, (DIDMetadata, DIDData)] = {
    ZIO
      .fromEither(did.createOperation)
      .mapError(e => DIDResolutionError.ValidationError(OperationValidationError.InvalidArgument(e)))
      .flatMap { op =>
        // unpublished CreateOperation (if exists) must be validated before the resolution
        ZIO
          .fromEither(didOpValidator.validate(op))
          .mapError(DIDResolutionError.ValidationError.apply)
          .as(op)
      }
      .map { op =>
        val metadata =
          DIDMetadata(
            lastOperationHash = ArraySeq.from(did.stateHash.toByteArray),
            canonicalId = None, // unpublished DID must not contain canonicalId
            deactivated = false, // unpublished DID cannot be deactivated
            created = None, // unpublished DID cannot have timestamp
            updated = None // unpublished DID cannot have timestamp
          )
        val didData = DIDData(
          id = did.asCanonical,
          publicKeys = op.publicKeys.collect { case pk: PublicKey => pk },
          services = op.services,
          internalKeys = op.publicKeys.collect { case pk: InternalPublicKey => pk },
          context = op.context
        )
        metadata -> didData
      }
  }

}
