package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.oid4vci.storage.InMemoryIssuanceSessionService
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.test.*

object OidcIssuanceAdapterSpec extends ZIOSpecDefault:

  override def spec = suite("OidcIssuanceAdapter")(
    test("protocolId is oid4vci") {
      val adapter = makeAdapter
      assertTrue(adapter.protocolId == ProtocolId("oid4vci"))
    },
    test("transport is OIDC") {
      val adapter = makeAdapter
      assertTrue(adapter.transport == TransportType.OIDC)
    },
    test("implements IssuanceProtocol contract") {
      val adapter: IssuanceProtocol = makeAdapter
      assertTrue(adapter.protocolId.value == "oid4vci")
    },
    test("processOffer is unsupported (OIDC uses redirects)") {
      val adapter = makeAdapter
      for result <- adapter.processOffer(ProtocolMessage("1", "offer", zio.json.ast.Json.Obj())).exit
      yield assertTrue(result.isFailure)
    },
  )

  private def makeAdapter: OidcIssuanceAdapter =
    OidcIssuanceAdapter(
      sessionStorage = InMemoryIssuanceSessionService(),
    )
