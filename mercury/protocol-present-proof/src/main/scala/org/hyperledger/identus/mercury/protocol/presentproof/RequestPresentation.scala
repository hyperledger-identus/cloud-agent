package org.hyperledger.identus.mercury.protocol.presentproof

import org.hyperledger.identus.mercury.model.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

final case class RequestPresentation(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = RequestPresentation.`type`,
    body: RequestPresentation.Body,
    attachments: Seq[AttachmentDescriptor],
    // extra
    thid: Option[String] = None,
    from: Option[DidId],
    to: Option[DidId],
) {

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = this.from,
    to = this.to.toSeq,
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get, // TODO get
    attachments = Some(this.attachments),
  )
}
object RequestPresentation {

  import AttachmentDescriptor.attachmentDescriptorEncoderV2
  given JsonEncoder[RequestPresentation] = DeriveJsonEncoder.gen
  given JsonDecoder[RequestPresentation] = DeriveJsonDecoder.gen

  // def `type`: PIURI = "https://didcomm.org/present-proof/3.0/request-presentation"
  def `type`: PIURI = "https://didcomm.atalaprism.io/present-proof/3.0/request-presentation"

  final case class Body(
      goal_code: Option[String] = None,
      comment: Option[String] = None,
      will_confirm: Option[Boolean] = Some(false), // Will send a ack message after the presentation

      // AtalaPrism Extension! //FIXME REMOVE
      // TODO This need to go to the attachment
      proof_types: Seq[ProofType] = Seq.empty // TODO  Move this to pollux
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def makePresentProofRequest(msg: Message): RequestPresentation = {
    val pp: ProposePresentation = ProposePresentation.readFromMessage(msg)

    RequestPresentation(
      body = RequestPresentation.Body(
        goal_code = pp.body.goal_code,
        comment = pp.body.comment,
      ),
      attachments = pp.attachments,
      thid = Some(msg.id),
      from = {
        assert(msg.to.length == 1, "The recipient is ambiguous. Need to have only 1 recipient") // TODO return error
        msg.to.headOption
      },
      to = msg.from,
    )
  }

  def readFromMessage(message: Message): RequestPresentation =
    val body = message.body.as[RequestPresentation.Body].toOption.get // TODO get

    RequestPresentation(
      id = message.id,
      `type` = message.piuri,
      body = body,
      attachments = message.attachments.getOrElse(Seq.empty),
      thid = message.thid,
      from = message.from,
      to = {
        assert(message.to.length == 1, "The recipient is ambiguous. Need to have only 1 recipient") // TODO return error
        message.to.headOption
      },
    )

}
