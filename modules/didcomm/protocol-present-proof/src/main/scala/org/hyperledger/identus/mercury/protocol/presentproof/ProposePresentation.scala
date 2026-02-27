package org.hyperledger.identus.mercury.protocol.presentproof

import org.hyperledger.identus.mercury.model.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

/** ALL parameterS are DIDCOMMV2 format and naming conventions and follows the protocol
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0454-present-proof-v2
  *
  * @param id
  * @param `type`
  * @param body
  * @param attachments
  */
final case class ProposePresentation(
    id: String = java.util.UUID.randomUUID.toString,
    `type`: PIURI = ProposePresentation.`type`,
    body: ProposePresentation.Body,
    attachments: Seq[AttachmentDescriptor] = Seq.empty[AttachmentDescriptor],
    // extra
    thid: Option[String] = None,
    from: DidId,
    to: DidId,
) {
  assert(`type` == ProposePresentation.`type`)

  def makeMessage: Message = Message(
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get, // TODO get
    attachments = Some(this.attachments)
  )
}

object ProposePresentation {
  // def `type`: PIURI = "https://didcomm.org/present-proof/3.0/propose-presentation"
  def `type`: PIURI = "https://didcomm.atalaprism.io/present-proof/3.0/propose-presentation"

  import AttachmentDescriptor.attachmentDescriptorEncoderV2
  given JsonEncoder[ProposePresentation] = DeriveJsonEncoder.gen
  given JsonDecoder[ProposePresentation] = DeriveJsonDecoder.gen

  /** @param goal_code
    * @param comment
    * @param formats
    */
  final case class Body(
      goal_code: Option[String] = None,
      comment: Option[String] = None,
      // AtalaPrism Extension!
      proof_types: Seq[ProofType] = Seq.empty
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def readFromMessage(message: Message): ProposePresentation = {
    val body = message.body.as[ProposePresentation.Body].toOption.get // TODO get

    ProposePresentation(
      id = message.id,
      `type` = message.piuri,
      body = body,
      attachments = message.attachments.getOrElse(Seq.empty),
      thid = message.thid,
      from = message.from.get, // TODO get
      to = {
        assert(message.to.length == 1, "The recipient is ambiguous. Need to have only 1 recipient") // TODO return error
        message.to.head
      },
    )
  }

}
