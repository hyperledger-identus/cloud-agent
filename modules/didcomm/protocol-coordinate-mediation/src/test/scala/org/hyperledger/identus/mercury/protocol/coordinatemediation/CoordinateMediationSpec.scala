package org.hyperledger.identus.mercury.protocol.coordinatemediation

import munit.*
import zio.json.DecoderOps

class CoordinateMediationSpec extends ZSuite {
  val mediateDenyExample = """{
    |  "id" : "e375a313-643b-4dc0-a747-74f688f056c4",
    |  "typ" : "application/didcomm-plain+json",
    |  "type" : "https://didcomm.org/coordinate-mediation/2.0/mediate-deny",
    |  "body" : {}
    |}""".stripMargin

  val mediateGrantExample = """{
    |  "id" : "bafa9e69-5ce0-411c-89eb-eb4bade6949d",
    |  "typ" : "application/didcomm-plain+json",
    |  "type" : "https://didcomm.org/coordinate-mediation/2.0/mediate-grant",
    |  "body" : {
    |    "routing_did" : "did:peer:2.Ez6LSoUkkHrYaDgGUdF6Vs6NnNyjahQmLKGsHhuZRy92qL1y4.Vz6MknogVLc4883Gys8GjzGZWnu4R1RjiCLDDrDxb7ToaDUeU.SeyJpZCI6Im5ldy1pZCIsInQiOiJkbSIsInMiOiJodHRwOi8vcm9vdHNpZC1tZWRpYXRvcjo4MDAwIiwiYSI6WyJkaWRjb21tL3YyIl19"
    |  }
    |}""".stripMargin

  test("parse mediate-grant") {
    val aux = mediateGrantExample.fromJson[MediateGrant]
    println(aux)
    assert(aux.isRight)
  }

  test("parse mediate-deny") {
    val aux = mediateDenyExample.fromJson[MediateDeny]
    assert(aux.isRight)
  }

}
