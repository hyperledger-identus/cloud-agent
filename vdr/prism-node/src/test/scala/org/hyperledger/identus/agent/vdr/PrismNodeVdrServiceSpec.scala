package org.hyperledger.identus.agent.vdr

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.{node_api, node_models}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import urlManagers.BaseUrlManager
import zio.*
import zio.test.*
import zio.test.Assertion.*

object PrismNodeVdrServiceSpec extends ZIOSpecDefault {

  private class RecordingSigner extends VdrOperationSigner {
    var lastCreate: Option[Array[Byte]] = None
    var lastUpdatePrev: Option[Array[Byte]] = None
    var lastDeactivatePrev: Option[Array[Byte]] = None

    private val signed = node_models.SignedAtalaOperation()

    override def signCreate(
        data: Array[Byte],
        didKeyId: Option[String]
    ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] = {
      lastCreate = Some(data)
      ZIO.succeed(signed)
    }

    override def signUpdate(
        previousEventHash: Array[Byte],
        data: Array[Byte],
        didKeyId: Option[String]
    ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] = {
      lastUpdatePrev = Some(previousEventHash)
      ZIO.succeed(signed)
    }

    override def signDeactivate(
        previousEventHash: Array[Byte],
        didKeyId: Option[String]
    ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] = {
      lastDeactivatePrev = Some(previousEventHash)
      ZIO.succeed(signed)
    }
  }

  private class StubPrismNodeClient(
      var scheduleResp: Task[node_api.ScheduleOperationsResponse],
      var entryResp: Task[node_api.GetVdrEntryResponse],
      var verifyResp: Task[node_api.VerifyVdrEntryResponse],
      var opInfoResp: Task[node_api.GetOperationInfoResponse]
  ) extends PrismNodeClient {
    override def scheduleOperations(
        req: node_api.ScheduleOperationsRequest
    ): Task[node_api.ScheduleOperationsResponse] = scheduleResp

    override def getVdrEntry(req: node_api.GetVdrEntryRequest): Task[node_api.GetVdrEntryResponse] =
      entryResp

    override def verifyVdrEntry(req: node_api.VerifyVdrEntryRequest): Task[node_api.VerifyVdrEntryResponse] =
      verifyResp

    override def getOperationInfo(req: node_api.GetOperationInfoRequest): Task[node_api.GetOperationInfoResponse] =
      opInfoResp
  }

  private val walletLayer = ZLayer.succeed(WalletAccessContext(WalletId.random))
  private val urlManager = BaseUrlManager("vdr://", "BaseURL")

  override def spec: Spec[TestEnvironment, Any] =
    suite("PrismNodeVdrService")(
      test("create maps prism-node output to URL and operation id") {
        val signer = new RecordingSigner
        val opId = ByteString.copyFromUtf8("op-create")
        val out = node_api.OperationOutput(
          result = node_api.OperationOutput.Result.CreateVdrEntryOutput(
            node_api.CreateVdrEntryOutput(eventHash = ByteString.copyFromUtf8("hash-1"))
          ),
          operationMaybe = node_api.OperationOutput.OperationMaybe.OperationId(opId)
        )
        val client = new StubPrismNodeClient(
          scheduleResp = ZIO.succeed(node_api.ScheduleOperationsResponse(outputs = Seq(out))),
          entryResp = ZIO.fail(new RuntimeException("unused")),
          verifyResp = ZIO.fail(new RuntimeException("unused")),
          opInfoResp = ZIO.fail(new RuntimeException("unused"))
        )
        val svc = new PrismNodeVdrService(client, signer, urlManager)

        for {
          res <- svc.create("payload".getBytes(), Map("m" -> "1"), None).provideLayer(walletLayer)
        } yield assertTrue(
          res.url.contains("drid=prism-node"),
          res.operationId.isDefined
        )
      },
      test("read returns VdrEntryNotFound when entry is deactivated") {
        val signer = new RecordingSigner
        val entry = node_api.VdrEntry(
          status = node_api.VdrEntryStatus.DEACTIVATED,
          eventHash = ByteString.copyFrom(Array[Byte](0x0a))
        )
        val client = new StubPrismNodeClient(
          scheduleResp = ZIO.fail(new RuntimeException("unused")),
          entryResp = ZIO.succeed(node_api.GetVdrEntryResponse(Some(entry))),
          verifyResp = ZIO.succeed(node_api.VerifyVdrEntryResponse(valid = true)),
          opInfoResp = ZIO.fail(new RuntimeException("unused"))
        )
        val svc = new PrismNodeVdrService(client, signer, urlManager)

        assertZIO(svc.read("0A").exit)(
          fails(isSubtype[VdrServiceError.VdrEntryNotFound](anything))
        )
      },
      test("update fetches latest head and passes previous hash to signer") {
        val signer = new RecordingSigner
        val previousHash = Array[Byte](1, 2, 3)
        val head = node_api.VdrEntry(
          status = node_api.VdrEntryStatus.ACTIVE,
          eventHash = ByteString.copyFrom(previousHash)
        )
        val opIdBytes = ByteString.copyFromUtf8("op-upd")
        val out = node_api.OperationOutput(
          result = node_api.OperationOutput.Result.UpdateVdrEntryOutput(
            node_api.UpdateVdrEntryOutput(eventHash = ByteString.copyFrom(Array[Byte](9, 9)))
          ),
          operationMaybe = node_api.OperationOutput.OperationMaybe.OperationId(opIdBytes)
        )
        val client = new StubPrismNodeClient(
          scheduleResp = ZIO.succeed(node_api.ScheduleOperationsResponse(outputs = Seq(out))),
          entryResp = ZIO.succeed(node_api.GetVdrEntryResponse(Some(head))),
          verifyResp = ZIO.succeed(node_api.VerifyVdrEntryResponse(valid = true)),
          opInfoResp = ZIO.fail(new RuntimeException("unused"))
        )
        val svc = new PrismNodeVdrService(client, signer, urlManager)

        for {
          resOpt <- svc.update("data".getBytes(), "0A0B", Map.empty, None).provideLayer(walletLayer)
        } yield assertTrue(
          signer.lastUpdatePrev.exists(_.sameElements(previousHash)),
          resOpt.flatMap(_.operationId).nonEmpty
        )
      }
    )
}
