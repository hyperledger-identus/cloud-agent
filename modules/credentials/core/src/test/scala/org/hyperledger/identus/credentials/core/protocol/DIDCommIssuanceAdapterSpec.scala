package org.hyperledger.identus.credentials.core.protocol

import org.hyperledger.identus.shared.models.*
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.test.*

import java.util.UUID

object DIDCommIssuanceAdapterSpec extends ZIOSpecDefault:

  override def spec = suite("DIDCommIssuanceAdapter")(
    test("protocolId is aries-issue-v3") {
      val adapter = makeAdapter
      assertTrue(adapter.protocolId == ProtocolId("aries-issue-v3"))
    },
    test("transport is DIDComm") {
      val adapter = makeAdapter
      assertTrue(adapter.transport == TransportType.DIDComm)
    },
    test("implements IssuanceProtocol contract") {
      val adapter: IssuanceProtocol = makeAdapter
      assertTrue(adapter.protocolId.value == "aries-issue-v3")
    },
    test("markSent with unsupported phase fails") {
      val adapter = makeAdapter
      val recordId = RecordId(UUID.randomUUID())
      for result <- adapter.markSent(recordId, Phase.Verification).exit
      yield assertTrue(result.isFailure)
    },
  )

  /** Creates an adapter with a null CredentialService — only tests that don't
    * actually invoke the service will pass. This validates contract conformance
    * and type-level correctness without heavy dependency setup.
    */
  private def makeAdapter: DIDCommIssuanceAdapter =
    DIDCommIssuanceAdapter(
      credentialService = null, // only used for contract-level tests
      walletCtx = WalletAccessContext(WalletId.random),
    )
