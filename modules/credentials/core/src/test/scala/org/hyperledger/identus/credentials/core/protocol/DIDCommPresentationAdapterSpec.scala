package org.hyperledger.identus.credentials.core.protocol

import org.hyperledger.identus.shared.models.*
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.test.*


object DIDCommPresentationAdapterSpec extends ZIOSpecDefault:

  override def spec = suite("DIDCommPresentationAdapter")(
    test("protocolId is aries-present-v3") {
      val adapter = makeAdapter
      assertTrue(adapter.protocolId == ProtocolId("aries-present-v3"))
    },
    test("transport is DIDComm") {
      val adapter = makeAdapter
      assertTrue(adapter.transport == TransportType.DIDComm)
    },
    test("implements PresentationProtocol contract") {
      val adapter: PresentationProtocol = makeAdapter
      assertTrue(adapter.protocolId.value == "aries-present-v3")
    },
    test("requestPresentation is unsupported during migration") {
      val adapter = makeAdapter
      for result <- adapter.requestPresentation(zio.json.ast.Json.Obj()).exit
      yield assertTrue(result.isFailure)
    },
  )

  private def makeAdapter: DIDCommPresentationAdapter =
    DIDCommPresentationAdapter(
      presentationService = null, // only used for contract-level tests
      walletCtx = WalletAccessContext(WalletId.random),
    )
