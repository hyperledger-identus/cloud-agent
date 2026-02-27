package org.hyperledger.identus.resolvers

import munit.*

/** resolver/testOnly org.hyperledger.identus.resolvers.DidValidatorSpec
  */
class DidValidatorSpec extends ZSuite {
  val exPRISM = "did:prism:66940961cc0f6a884ff5876992991b994ca518aa34b3bacfd15f2b51a7b042cf"
  val exPeer =
    "did:peer:2.Ez6LSeSTchYyPTBk131pKECXWP7t1CYG2RMgRE2KWoiWi962w.Vz6MkmLJC9YyMerhFD831jrbVAo8rHXiBvDV6UKnt8xzQY7MJ.SeyJ0IjoiZG0iLCJzIjoiaHR0cDovL2xvY2FsaG9zdEw5OTk5IiwiciI6W10sImEiOlsiZGlkY29tbS92MiJdfQ"

  test("validDID") {
    assertEquals(DidValidator.validDID(exPRISM), true)
    assertEquals(DidValidator.validDID(exPeer), true)
    assertEquals(DidValidator.validDID("did:test:ola"), true)
  }

  test("supportedDid") {
    assertEquals(DidValidator.supportedDid(exPRISM), true)
    assertEquals(DidValidator.supportedDid(exPeer), true)
    assertEquals(DidValidator.supportedDid("did:test:ola"), false)
  }

  test("isDidPRISM and isDidPeer") {
    assertEquals(DidValidator.isDidPRISM(exPRISM), true)
    assertEquals(DidValidator.isDidPeer(exPRISM), false)

    assertEquals(DidValidator.isDidPRISM(exPeer), false)
    assertEquals(DidValidator.isDidPeer(exPeer), true)
  }
}
