package org.hyperledger.identus.shared.models

import zio.test.*

object CapabilitySpec extends ZIOSpecDefault:
  def spec = suite("Capability")(
    test("exact match") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", Some("eddsa"))
      assertTrue(cap.satisfies(req))
    },
    test("wildcard match - provider with variant satisfies any-variant requirement") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", None)
      assertTrue(cap.satisfies(req))
    },
    test("no match - different contract") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialBuilder", Some("eddsa"))
      assertTrue(!cap.satisfies(req))
    },
    test("no match - different variant") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", Some("es256"))
      assertTrue(!cap.satisfies(req))
    },
  )
