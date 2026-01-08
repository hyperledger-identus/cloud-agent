package org.hyperledger.identus.mercury.protocol.issuecredential

import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

final case class RequestCredential(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = RequestCredential.`type`,
    body: RequestCredential.Body,
    attachments: Seq[AttachmentDescriptor],
    // extra
    thid: Option[String] = None,
    from: DidId,
    to: DidId,
) extends ReadAttachmentsUtils {

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get, // TODO get
    attachments = Some(this.attachments),
  )
}
object RequestCredential {

  import AttachmentDescriptor.attachmentDescriptorEncoderV2
  given JsonEncoder[RequestCredential] = DeriveJsonEncoder.gen
  given JsonDecoder[RequestCredential] = DeriveJsonDecoder.gen

  def `type`: PIURI = "https://didcomm.org/issue-credential/3.0/request-credential"

  def build(
      fromDID: DidId,
      toDID: DidId,
      thid: Option[String] = None,
      credentials: Seq[(IssueCredentialRequestFormat, Array[Byte])] = Seq.empty,
  ): RequestCredential = {
    val attachments = credentials.map { case (format, singleCredential) =>
      AttachmentDescriptor.buildBase64Attachment(payload = singleCredential, format = Some(format.name))
    }.toSeq
    RequestCredential(
      thid = thid,
      from = fromDID,
      to = toDID,
      body = Body(),
      attachments = attachments
    )
  }

  final case class Body(
      goal_code: Option[String] = None,
      comment: Option[String] = None,
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def makeRequestCredentialFromOffer(oc: OfferCredential): RequestCredential =
    RequestCredential(
      body = RequestCredential.Body(goal_code = oc.body.goal_code, comment = oc.body.comment),
      attachments = oc.attachments,
      thid = oc.thid.orElse(Some(oc.id)),
      from = oc.to.getOrElse(throw new IllegalArgumentException("OfferCredential must have a recipient")),
      to = oc.from,
    )

  def readFromMessage(message: Message): Either[String, RequestCredential] = {
    message.body.as[RequestCredential.Body] match
      case Left(err)   => Left("Fail to parse RequestCredential's body: " + err)
      case Right(body) =>
        message.from match
          case None       => Left("RequestCredential MUST have the sender explicit")
          case Some(from) =>
            message.to match
              case firstTo +: Seq() =>
                Right(
                  RequestCredential(
                    id = message.id,
                    `type` = message.piuri,
                    body = body,
                    attachments = message.attachments.getOrElse(Seq.empty),
                    thid = message.thid,
                    from = from,
                    to = firstTo,
                  )
                )
              case tos => Left(s"RequestCredential MUST have only 1 recipient instead has '${tos}'")
  }

}
