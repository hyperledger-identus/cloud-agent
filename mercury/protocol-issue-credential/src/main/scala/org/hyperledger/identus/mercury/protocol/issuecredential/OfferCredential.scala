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
  * @param thid
  *   needs to be used need replying
  */
final case class OfferCredential(
    id: String = java.util.UUID.randomUUID.toString,
    `type`: PIURI = OfferCredential.`type`,
    body: OfferCredential.Body,
    attachments: Seq[AttachmentDescriptor],
    // extra
    thid: Option[String] = None,
    from: DidId,
    to: Option[DidId] = None,
) extends ReadAttachmentsUtils {
  assert(`type` == OfferCredential.`type`)

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = to.toSeq,
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get, // TODO get
    attachments = Some(this.attachments),
  )
}

object OfferCredential {

  import AttachmentDescriptor.attachmentDescriptorEncoderV2
  given JsonEncoder[OfferCredential] = DeriveJsonEncoder.gen
  given JsonDecoder[OfferCredential] = DeriveJsonDecoder.gen

  def `type`: PIURI = "https://didcomm.org/issue-credential/3.0/offer-credential"

  def build(
      fromDID: DidId,
      toDID: DidId,
      thid: Option[String] = None,
      credential_preview: CredentialPreview,
      credentials: Seq[(IssueCredentialOfferFormat, Array[Byte])],
  ): OfferCredential = {
    val attachments = credentials.map { case (format, singleCredential) =>
      AttachmentDescriptor.buildBase64Attachment(payload = singleCredential, format = Some(format.name))
    }.toSeq
    OfferCredential(
      thid = thid,
      from = fromDID,
      to = Some(toDID),
      body = Body(credential_preview = credential_preview),
      attachments = attachments
    )
  }

  final case class Body(
      goal_code: Option[String] = None,
      comment: Option[String] = None,
      replacement_id: Option[String] = None,
      multiple_available: Option[String] = None,
      credential_preview: CredentialPreview,
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def makeOfferToProposeCredential(
      msg: Message // TODO change msg: Message to ProposeCredential
  ): Either[String, OfferCredential] =
    ProposeCredential.readFromMessage(msg).flatMap { pc =>
      pc.body.credential_preview match
        case None => Left("This method expects the ProposeCredential to have a 'credential_preview' in the body")
        case Some(credential_preview) =>
          Right(
            OfferCredential(
              body = OfferCredential.Body(
                goal_code = pc.body.goal_code,
                comment = pc.body.comment,
                replacement_id = None,
                multiple_available = None,
                credential_preview = credential_preview,
              ),
              attachments = pc.attachments,
              thid = msg.thid.orElse(Some(pc.id)),
              from = pc.to,
              to = Some(pc.from),
            )
          )
    }

  def readFromMessage(message: Message): Either[String, OfferCredential] = {
    message.body.as[OfferCredential.Body] match
      case Left(err)   => Left("Fail to parse OfferCredential's body: " + err)
      case Right(body) =>
        message.from match
          case None       => Left("OfferCredential MUST have the sender explicit")
          case Some(from) =>
            message.to match
              case firstTo +: Seq() =>
                Right(
                  OfferCredential(
                    id = message.id,
                    `type` = message.piuri,
                    body = body,
                    attachments = message.attachments.getOrElse(Seq.empty),
                    thid = message.thid,
                    from = from,
                    to = Some(firstTo),
                  )
                )
              case tos => Left(s"OfferCredential MUST have only 1 recipient instead has '${tos}'")
  }
}
