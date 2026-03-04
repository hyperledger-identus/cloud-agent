package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.test.*

object OidcPresentationAdapterSpec extends ZIOSpecDefault:

  override def spec = suite("OidcPresentationAdapter")(
    test("protocolId is oid4vp") {
      val adapter = OidcPresentationAdapter()
      assertTrue(adapter.protocolId == ProtocolId("oid4vp"))
    },
    test("transport is OIDC") {
      val adapter = OidcPresentationAdapter()
      assertTrue(adapter.transport == TransportType.OIDC)
    },
    test("implements PresentationProtocol contract") {
      val adapter: PresentationProtocol = OidcPresentationAdapter()
      assertTrue(adapter.protocolId.value == "oid4vp")
    },
    test("requestPresentation is unsupported (uses redirect)") {
      val adapter = OidcPresentationAdapter()
      for result <- adapter.requestPresentation(zio.json.ast.Json.Obj()).exit
      yield assertTrue(result.isFailure)
    },
  )
