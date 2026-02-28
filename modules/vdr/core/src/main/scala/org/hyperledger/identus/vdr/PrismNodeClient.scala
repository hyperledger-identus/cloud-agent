package org.hyperledger.identus.vdr

import io.iohk.atala.prism.protos.node_api
import zio.*

/** Thin wrapper around the gRPC stub so it can be replaced in tests. */
trait PrismNodeClient {
  def scheduleOperations(
      req: node_api.ScheduleOperationsRequest
  ): Task[node_api.ScheduleOperationsResponse]
  def getVdrEntry(req: node_api.GetVdrEntryRequest): Task[node_api.GetVdrEntryResponse]
  def verifyVdrEntry(req: node_api.VerifyVdrEntryRequest): Task[node_api.VerifyVdrEntryResponse]
  def getOperationInfo(req: node_api.GetOperationInfoRequest): Task[node_api.GetOperationInfoResponse]
}

final class PrismNodeGrpcClient(
    stub: node_api.NodeServiceGrpc.NodeServiceBlockingStub
) extends PrismNodeClient {
  override def scheduleOperations(
      req: node_api.ScheduleOperationsRequest
  ): Task[node_api.ScheduleOperationsResponse] =
    ZIO.attemptBlocking(stub.scheduleOperations(req))

  override def getVdrEntry(req: node_api.GetVdrEntryRequest): Task[node_api.GetVdrEntryResponse] =
    ZIO.attemptBlocking(stub.getVdrEntry(req))

  override def verifyVdrEntry(req: node_api.VerifyVdrEntryRequest): Task[node_api.VerifyVdrEntryResponse] =
    ZIO.attemptBlocking(stub.verifyVdrEntry(req))

  override def getOperationInfo(req: node_api.GetOperationInfoRequest): Task[node_api.GetOperationInfoResponse] =
    ZIO.attemptBlocking(stub.getOperationInfo(req))
}
