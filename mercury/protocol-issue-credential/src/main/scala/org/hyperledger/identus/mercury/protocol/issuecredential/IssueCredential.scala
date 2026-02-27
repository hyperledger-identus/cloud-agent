package org.hyperledger.identus.mercury.protocol.issuecredential

import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

/** ALL parameterS are DIDCOMMV2 format and naming conventions and follows the protocol
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2
  *
  * @param id
  * @param `type`
  * @param body
  * @param attachments
  */
final case class IssueCredential(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = IssueCredential.`type`,
    body: IssueCredential.Body,
    attachments: Seq[AttachmentDescriptor],
    // extra
    thid: Option[String] = None,
    from: DidId,
    to: DidId,
) extends ReadAttachmentsUtils {
  assert(`type` == IssueCredential.`type`)

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get,
    attachments = Some(this.attachments),
  )
}

object IssueCredential {

  import AttachmentDescriptor.attachmentDescriptorEncoderV2
  given JsonEncoder[IssueCredential] = DeriveJsonEncoder.gen
  given JsonDecoder[IssueCredential] = DeriveJsonDecoder.gen

  def `type`: PIURI = "https://didcomm.org/issue-credential/3.0/issue-credential"

  def build(
      fromDID: DidId,
      toDID: DidId,
      thid: Option[String] = None,
      credentials: Seq[(IssueCredentialIssuedFormat, Array[Byte])],
  ): IssueCredential = {
    val attachments = credentials.map { case (format, singleCredential) =>
      AttachmentDescriptor.buildBase64Attachment(payload = singleCredential, format = Some(format.name))
    }.toSeq
    IssueCredential(
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
      replacement_id: Option[String] = None,
      more_available: Option[String] = None,
  )
  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def makeIssueCredentialFromRequestCredential(msg: Message): Either[String, IssueCredential] =
    RequestCredential.readFromMessage(msg).map { rc =>
      IssueCredential(
        body = IssueCredential.Body(
          goal_code = rc.body.goal_code,
          comment = rc.body.comment,
          replacement_id = None,
          more_available = None,
        ),
        attachments = rc.attachments,
        thid = msg.thid.orElse(Some(rc.id)),
        from = rc.to,
        to = rc.from,
      )
    }

  def readFromMessage(message: Message): Either[String, IssueCredential] = {
    message.body.as[IssueCredential.Body] match
      case Left(err)   => Left("Fail to parse IssueCredential's body: " + err)
      case Right(body) =>
        message.from match
          case None       => Left("IssueCredential MUST have the sender explicit")
          case Some(from) =>
            message.to match
              case firstTo +: Seq() =>
                Right(
                  IssueCredential(
                    id = message.id,
                    `type` = message.piuri,
                    body = body,
                    attachments = message.attachments.getOrElse(Seq.empty),
                    thid = message.thid,
                    from = from,
                    to = firstTo
                  )
                )
              case tos => Left(s"IssueCredential MUST have only 1 recipient instead has '${tos}'")
  }

}
