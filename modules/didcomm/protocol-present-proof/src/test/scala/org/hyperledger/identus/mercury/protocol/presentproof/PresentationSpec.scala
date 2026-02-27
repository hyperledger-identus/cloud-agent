package org.hyperledger.identus.mercury.protocol.presentproof

import munit.*
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId, LinkData}
import org.hyperledger.identus.mercury.model.AttachmentDescriptor.attachmentDescriptorEncoderV2
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

class PresentationSpec extends ZSuite {

  test("Verifier Presentation") {

    val body = Presentation.Body(goal_code = Some("Presentation"))
    val attachmentDescriptor =
      AttachmentDescriptor("1", Some("application/json"), LinkData(links = Seq("http://test"), hash = "1234"))
    val attachmentDescriptorJson = attachmentDescriptor.toJson

    val expectedProposalJson = s"""{
                         |    "id": "061bf917-2cbe-460b-8d12-b1a9609505c2",
                         |    "type": "https://didcomm.atalaprism.io/present-proof/3.0/presentation",
                         |    "body": {"goal_code": "Presentation"},
                         |    "attachments": [$attachmentDescriptorJson],
                         |    "to" : "did:prism:test123",
                         |    "from" : "did:prism:test123"
                         |}""".stripMargin.fromJson[Json]

    val presentation = Presentation(
      id = "061bf917-2cbe-460b-8d12-b1a9609505c2",
      body = body,
      attachments = Seq(attachmentDescriptor),
      to = DidId("did:prism:test123"),
      from = DidId("did:prism:test123")
    )

    val result = presentation.toJsonAST
    assertEquals(result, expectedProposalJson)
  }
}
