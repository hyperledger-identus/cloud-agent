package org.hyperledger.identus.mercury.protocol.reportproblem.v2

import munit.*
import org.hyperledger.identus.mercury.model.DidId
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.{Json, JsonCursor}
class ReportProblemSpec extends ZSuite {

  test("ReportProblem") {

    // val reportproblem = ReportProblem.build("")
    val fromDid = DidId("did:prism:test1")
    val toDid = DidId("did:prism:test2")

    val body = ReportProblem.Body(
      code = ProblemCode("e.p.xfer.cant-use-endpoint"),
      comment = Some("Unable to use the {1} endpoint for {2}."),
      args = Some(Seq("https://agents.r.us/inbox", "did:sov:C805sNYhMrjHiqZDTUASHg")),
      escalate_to = Some("mailto:admin@foo.org")
    )
    val reportproblem = ReportProblem(
      id = "7c9de639-c51c-4d60-ab95-103fa613c805",
      from = fromDid,
      to = toDid,
      thid = None,
      pthid = Some("1e513ad4-48c9-444e-9e7e-5b8b45c5e325"),
      ack = Some(Seq("1e513ad4-48c9-444e-9e7e-5b8b45c5e325")),
      body = body
    )

    val expectedProposalJson = s"""{
  "from" : "did:prism:test1",      
  "to" : "did:prism:test2", 
  "type": "https://didcomm.org/report-problem/2.0/problem-report",
  "id": "7c9de639-c51c-4d60-ab95-103fa613c805",
  "pthid": "1e513ad4-48c9-444e-9e7e-5b8b45c5e325",
  "ack": ["1e513ad4-48c9-444e-9e7e-5b8b45c5e325"],
  "body": {
    "code": "e.p.xfer.cant-use-endpoint",
    "comment": "Unable to use the {1} endpoint for {2}.",
    "args": [
      "https://agents.r.us/inbox",
      "did:sov:C805sNYhMrjHiqZDTUASHg"
    ],
    "escalate_to": "mailto:admin@foo.org"
  }
}""".stripMargin.fromJson[Json]
    val result = reportproblem.toJsonAST

    assertEquals(result, expectedProposalJson)
  }

  test("ReportProblem.build") {

    val fromDid = DidId("did:prism:test1")
    val toDid = DidId("did:prism:test2")

    val reportProblem = ReportProblem.build(
      fromDID = fromDid,
      toDID = toDid,
      pthid = "1e513ad4-48c9-444e-9e7e-5b8b45c5e325",
      code = ProblemCode("e.p.xfer.cant-use-endpoint"),
      comment = Some("Unable to use the {1} endpoint for {2}.")
    )

    val result = reportProblem.toJsonAST

    assertEquals(
      result.flatMap(_.get(JsonCursor.field("pthid").isString)).map(_.value),
      Right("1e513ad4-48c9-444e-9e7e-5b8b45c5e325")
    )
    assertEquals(
      result.flatMap(_.get(JsonCursor.field("from").isString)).map(_.value),
      Right("did:prism:test1")
    )
    assertEquals(
      result.flatMap(_.get(JsonCursor.field("to").isString)).map(_.value),
      Right("did:prism:test2")
    )
    assertEquals(
      result.flatMap(_.get(JsonCursor.field("type").isString)).map(_.value),
      Right("https://didcomm.org/report-problem/2.0/problem-report")
    )
    assertEquals(
      result.flatMap(_.get(JsonCursor.field("body").isObject >>> JsonCursor.field("code").isString)).map(_.value),
      Right("e.p.xfer.cant-use-endpoint")
    )
    assertEquals(
      result.flatMap(_.get(JsonCursor.field("body").isObject >>> JsonCursor.field("comment").isString)).map(_.value),
      Right("Unable to use the {1} endpoint for {2}.")
    )
  }

}
