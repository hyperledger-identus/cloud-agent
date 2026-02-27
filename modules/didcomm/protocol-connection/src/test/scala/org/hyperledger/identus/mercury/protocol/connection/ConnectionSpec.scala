package org.hyperledger.identus.mercury.protocol.connection

import munit.*
import org.hyperledger.identus.mercury.model.{DidId, Message}
import zio.json.DecoderOps

/** protocolConnection/testOnly org.hyperledger.identus.mercury.protocol.connection.CoordinateMediationSpec */
class CoordinateMediationSpec extends ZSuite {

  test("parse ConnectionRequest") {
    val connectionRequest = """{
        |  "id" : "46430433-d872-4d4c-8376-0fa72cf124c2",
        |  "thid" : "544659a7-a8e3-4101-a918-6afef6ad7bbb",
        |  "pthid": "some-pthid",
        |  "typ" : "application/didcomm-plain+json",
        |  "type" : "https://atalaprism.io/mercury/connections/1.0/request",
        |  "body" : {"goal_code": "Connect"},
        |  "from" : "did:test:alice",
        |  "to" : ["did:test:bob"]
        |}""".stripMargin
    val aux = connectionRequest
      .fromJson[Message]
      .left
      .map(err => fail(err)) // fail / get error
      .flatMap(e => ConnectionRequest.fromMessage(e))
    assertEquals(
      aux,
      Right(
        ConnectionRequest(
          `type` = "https://atalaprism.io/mercury/connections/1.0/request",
          id = "46430433-d872-4d4c-8376-0fa72cf124c2",
          from = DidId("did:test:alice"),
          to = DidId("did:test:bob"),
          thid = Some("544659a7-a8e3-4101-a918-6afef6ad7bbb"),
          pthid = Some("some-pthid"),
          body = ConnectionRequest.Body(goal_code = Some("Connect")),
        )
      )
    )
  }

  test("parse ConnectionResponse") {
    val obj = ConnectionResponse(
      id = "b7878bfc-16d5-49dd-a443-4e87a3c4c8c6",
      from = DidId(
        "did:peer:2.Ez6LSpwvTbwvMF5xtSZ6uNoZvWNcPGx1J2ziuais63CpB1UDe.Vz6MkmutH2XW9ybLtSyYRvYcyUbUPWneev6oVu9zfoEmFxQ2y.SeyJ0IjoiZG0iLCJzIjoiaHR0cDovL2hvc3QuZG9ja2VyLmludGVybmFsOjgwODAvZGlkY29tbSIsInIiOltdLCJhIjpbImRpZGNvbW0vdjIiXX0"
      ),
      to = DidId(
        "did:peer:2.Ez6LSr1TzNDH5S4GMtn1ELG6P6xBdLcFxQ8wBaZCn8bead7iK.Vz6MknkPqgbvK4c7GhsKzi2EyBV4rZbvtygJqxM4Eh8EF5DGB.SeyJyIjpbImRpZDpwZWVyOjIuRXo2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5uby5WejZNa2pqQ3F5SkZUSHFpWGtZUndYcVhTZlo2WWtVMjFyMzdENkFCN1hLMkhZNXQyLlNleUpwWkNJNkltNWxkeTFwWkNJc0luUWlPaUprYlNJc0luTWlPaUpvZEhSd2N6b3ZMMjFsWkdsaGRHOXlMbkp2YjNSemFXUXVZMnh2ZFdRaUxDSmhJanBiSW1ScFpHTnZiVzB2ZGpJaVhYMCM2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5ubyJdLCJzIjoiaHR0cHM6Ly9tZWRpYXRvci5yb290c2lkLmNsb3VkIiwiYSI6WyJkaWNvbW0vdjIiXSwidCI6ImRtIn0"
      ),
      thid = None,
      pthid = Some("52dc177a-05dc-4deb-ab57-ac9d5e3ff10c"),
      body = ConnectionResponse.Body(
        goal_code = Some("connect"),
        goal = Some("Establish a trust connection between two peers"),
        accept = Some(Seq.empty)
      ),
    )

    val connectionRequest = """{
      |  "type" : "https://atalaprism.io/mercury/connections/1.0/response",
      |  "id" : "b7878bfc-16d5-49dd-a443-4e87a3c4c8c6",
      |  "from" : "did:peer:2.Ez6LSpwvTbwvMF5xtSZ6uNoZvWNcPGx1J2ziuais63CpB1UDe.Vz6MkmutH2XW9ybLtSyYRvYcyUbUPWneev6oVu9zfoEmFxQ2y.SeyJ0IjoiZG0iLCJzIjoiaHR0cDovL2hvc3QuZG9ja2VyLmludGVybmFsOjgwODAvZGlkY29tbSIsInIiOltdLCJhIjpbImRpZGNvbW0vdjIiXX0",
      |  "to" : "did:peer:2.Ez6LSr1TzNDH5S4GMtn1ELG6P6xBdLcFxQ8wBaZCn8bead7iK.Vz6MknkPqgbvK4c7GhsKzi2EyBV4rZbvtygJqxM4Eh8EF5DGB.SeyJyIjpbImRpZDpwZWVyOjIuRXo2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5uby5WejZNa2pqQ3F5SkZUSHFpWGtZUndYcVhTZlo2WWtVMjFyMzdENkFCN1hLMkhZNXQyLlNleUpwWkNJNkltNWxkeTFwWkNJc0luUWlPaUprYlNJc0luTWlPaUpvZEhSd2N6b3ZMMjFsWkdsaGRHOXlMbkp2YjNSemFXUXVZMnh2ZFdRaUxDSmhJanBiSW1ScFpHTnZiVzB2ZGpJaVhYMCM2TFNrV05SZ3k1d1pNTTJOQjg4aDRqakJwN0U4N0xLTXdkUGVCTFRjbUNabm5ubyJdLCJzIjoiaHR0cHM6Ly9tZWRpYXRvci5yb290c2lkLmNsb3VkIiwiYSI6WyJkaWNvbW0vdjIiXSwidCI6ImRtIn0",
      |  "pthid" : "52dc177a-05dc-4deb-ab57-ac9d5e3ff10c",
      |  "body" : {
      |    "goal_code" : "connect",
      |    "goal" : "Establish a trust connection between two peers",
      |    "accept" : [
      |    ]
      |  }
      |}""".stripMargin

    assertEquals(
      connectionRequest.fromJson[ConnectionResponse],
      Right(obj)
    )
  }

}
