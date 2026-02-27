package org.hyperledger.identus.mercury.protocol.issuecredential

import munit.*
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId}
import org.hyperledger.identus.mercury.model.AttachmentDescriptor.attachmentDescriptorEncoderV2
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

class ProposeCredentialSpec extends ZSuite {

  test("Holder ProposeCredential") {

    val attribute1 = Attribute(name = "name", value = "Joe Blog")
    val attribute2 = Attribute(name = "dob", value = "01/10/1947")
    val credentialPreview = CredentialPreview(attributes = Seq(attribute1, attribute2))
    val body =
      ProposeCredential.Body(goal_code = Some("Propose Credential"), credential_preview = Some(credentialPreview))
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment[CredentialPreview](payload = credentialPreview)
    val attachmentDescriptorJson = attachmentDescriptor.toJson

    val expectedProposalJson =
      s"""{
         |  "id": "031bf917-2cbe-460b-8d12-b1a9609505c2",
         |  "type": "https://didcomm.org/issue-credential/3.0/propose-credential",
         |  "body": {
         |    "goal_code": "Propose Credential",
         |    "credential_preview": {
         |      "type": "https://didcomm.org/issue-credential/3.0/credential-credential",
         |      "body" : {
         |        "attributes": [
         |          { "name": "name", "value": "Joe Blog" },
         |          { "name": "dob", "value": "01/10/1947" }
         |        ]
         |      }
         |    }
         |  },
         |  "attachments": [
         |    $attachmentDescriptorJson
         |  ],
         |  "to" : "did:prism:test123",
         |  "from" : "did:prism:test123"
         |}""".stripMargin.fromJson[Json]

    val proposeCredential = ProposeCredential(
      id = "031bf917-2cbe-460b-8d12-b1a9609505c2",
      body = body,
      attachments = Seq(attachmentDescriptor),
      to = DidId("did:prism:test123"),
      from = DidId("did:prism:test123"),
    )

    val result = proposeCredential.toJsonAST
    assertEquals(result, expectedProposalJson)
  }
}
