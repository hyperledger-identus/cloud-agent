package org.hyperledger.identus.castor.core.service

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.{node_api, node_models}
import io.iohk.atala.prism.protos.common_models.{HealthCheckRequest, HealthCheckResponse}
import io.iohk.atala.prism.protos.node_api.{
  GetDidDocumentRequest,
  GetDidDocumentResponse,
  GetLastSyncedBlockTimestampRequest,
  GetLastSyncedBlockTimestampResponse,
  GetNodeBuildInfoRequest,
  GetNodeBuildInfoResponse,
  GetNodeNetworkProtocolInfoRequest,
  GetNodeNetworkProtocolInfoResponse,
  GetOperationInfoRequest,
  GetOperationInfoResponse,
  NodeServiceGrpc,
  ScheduleOperationsRequest,
  ScheduleOperationsResponse
}
import org.hyperledger.identus.castor.core.model.did.{DIDData, PrismDID, PrismDIDOperation}
import org.hyperledger.identus.castor.core.model.error.DIDResolutionError
import org.hyperledger.identus.castor.core.util.{DIDOperationValidator, GenUtils}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.concurrent.Future

object DIDServiceSpec extends ZIOSpecDefault {

  private val notFoundDidDocumentResponse = GetDidDocumentResponse(
    document = None,
    lastSyncedBlockTimestamp = None,
    lastUpdateOperation = ByteString.EMPTY
  )

  private def mockNodeService(
      createOperation: PrismDIDOperation.Create
  ): ULayer[node_api.NodeServiceGrpc.NodeService] = {
    import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*

    val operationProto = createOperation.toProto
    val didData = node_models.DIDData(
      id = createOperation.did.suffix.toString,
      publicKeys = operationProto.value.didData.get.publicKeys,
      services = operationProto.value.didData.get.services,
      context = operationProto.value.didData.get.context
    )

    mockNodeService(
      GetDidDocumentResponse(
        document = Some(didData),
        lastSyncedBlockTimestamp = None,
        lastUpdateOperation = ByteString.EMPTY
      )
    )
  }

  private def mockNodeService(
      didData: GetDidDocumentResponse = notFoundDidDocumentResponse
  ): ULayer[node_api.NodeServiceGrpc.NodeService] = ZLayer.succeed {
    new NodeServiceGrpc.NodeService:
      override def healthCheck(request: HealthCheckRequest): Future[HealthCheckResponse] =
        Future.failed(throw new NotImplementedError)

      override def getDidDocument(request: GetDidDocumentRequest): Future[GetDidDocumentResponse] =
        Future.successful(didData)

      override def getNodeBuildInfo(request: GetNodeBuildInfoRequest): Future[GetNodeBuildInfoResponse] =
        Future.failed(throw new NotImplementedError)

      override def getNodeNetworkProtocolInfo(
          request: GetNodeNetworkProtocolInfoRequest
      ): Future[GetNodeNetworkProtocolInfoResponse] = Future.failed(throw new NotImplementedError)

      override def getOperationInfo(request: GetOperationInfoRequest): Future[GetOperationInfoResponse] =
        Future.failed(throw new NotImplementedError)

      override def getLastSyncedBlockTimestamp(
          request: GetLastSyncedBlockTimestampRequest
      ): Future[GetLastSyncedBlockTimestampResponse] = Future.failed(throw new NotImplementedError)

      override def scheduleOperations(request: ScheduleOperationsRequest): Future[ScheduleOperationsResponse] =
        Future.failed(throw new NotImplementedError)

      override def createVdrEntry(
          request: node_api.CreateVdrEntryRequest
      ): Future[node_api.CreateVdrEntryResponse] = Future.failed(throw new NotImplementedError)

      override def updateVdrEntry(
          request: node_api.UpdateVdrEntryRequest
      ): Future[node_api.UpdateVdrEntryResponse] = Future.failed(throw new NotImplementedError)

      override def deactivateVdrEntry(
          request: node_api.DeactivateVdrEntryRequest
      ): Future[node_api.DeactivateVdrEntryResponse] = Future.failed(throw new NotImplementedError)

      override def getVdrEntry(request: node_api.GetVdrEntryRequest): Future[node_api.GetVdrEntryResponse] =
        Future.failed(throw new NotImplementedError)

      override def verifyVdrEntry(
          request: node_api.VerifyVdrEntryRequest
      ): Future[node_api.VerifyVdrEntryResponse] = Future.failed(throw new NotImplementedError)
  }

  private def didServiceLayer(): ULayer[DIDService] =
    DIDOperationValidator.layer() ++ mockNodeService() >>> PrismNodeDIDService.layer

  private def didServiceLayer(createOperation: PrismDIDOperation.Create): ULayer[DIDService] =
    DIDOperationValidator.layer() ++ mockNodeService(
      createOperation: PrismDIDOperation.Create
    ) >>> PrismNodeDIDService.layer

  override def spec = suite("PrismNodeDIDService")(resolveDIDSpec.provide(didServiceLayer()), resolveDIDMetadataSpec)

  private val resolveDIDSpec = suite("resolveDID")(
    test("long-form unpublished DID returns content in encoded state") {
      check(GenUtils.createOperation) { operation =>
        val prismDID = PrismDID.buildLongFormFromOperation(operation)
        for {
          svc <- ZIO.service[DIDService]
          resolutionResult <- svc.resolveDID(prismDID).someOrFailException
          (didMetadata, didData) = resolutionResult
        } yield assert(didMetadata.deactivated)(isFalse) &&
          assert(didData.publicKeys ++ didData.internalKeys)(hasSameElements(operation.publicKeys)) &&
          assert(didData.services)(equalTo(operation.services)) &&
          assert(didData.context)(equalTo(operation.context))
      }
    },
    test("long-form unpublished DID cannot be resolved if encoded state is invalid") {
      for {
        svc <- ZIO.service[DIDService]
        prismDID = PrismDID.buildLongFormFromOperation(PrismDIDOperation.Create(Nil, Nil, Nil))
        exit <- svc.resolveDID(prismDID).exit
      } yield assert(exit)(fails(isSubtype[DIDResolutionError.ValidationError](anything)))
    },
    test("short-form unpublished DID always return None") {
      check(GenUtils.createOperation) { operation =>
        val prismDID = PrismDID.buildLongFormFromOperation(operation).asCanonical
        for {
          svc <- ZIO.service[DIDService]
          resolutionResult <- svc.resolveDID(prismDID)
        } yield assert(resolutionResult)(isNone)
      }
    }
  )

  private val resolveDIDMetadataSpec = suite("resolveDID metadata")(
    test("short-form published DID doesn't return canonicalID")(
      for {
        operation <- GenUtils.createOperation.runCollectN(1).map(_.head)
        prismDID = PrismDID.buildLongFormFromOperation(operation)
        svc <- ZIO.service[DIDService].provide(didServiceLayer(operation))
        resolutionResult <- svc.resolveDID(prismDID.asCanonical).someOrFailException
        (didMetadata, _) = resolutionResult
      } yield assert(didMetadata.canonicalId)(isNone)
    ),
    test("long-form published DID return canonicalID")(
      for {
        operation <- GenUtils.createOperation.runCollectN(1).map(_.head)
        prismDID = PrismDID.buildLongFormFromOperation(operation)
        svc <- ZIO.service[DIDService].provide(didServiceLayer(operation))
        resolutionResult <- svc.resolveDID(prismDID).someOrFailException
        (didMetadata, _) = resolutionResult
      } yield assert(didMetadata.canonicalId)(isSome(equalTo(prismDID.asCanonical)))
    ),
    test("long-form unpublished DID doesn't return canonicalID")(
      for {
        operation <- GenUtils.createOperation.runCollectN(1).map(_.head)
        prismDID = PrismDID.buildLongFormFromOperation(operation)
        svc <- ZIO.service[DIDService].provide(didServiceLayer())
        resolutionResult <- svc.resolveDID(prismDID).someOrFailException
        (didMetadata, _) = resolutionResult
      } yield assert(didMetadata.canonicalId)(isNone)
    ),
  )
}
