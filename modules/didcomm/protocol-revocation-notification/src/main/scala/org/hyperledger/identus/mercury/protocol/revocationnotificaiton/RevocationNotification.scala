package org.hyperledger.identus.mercury.protocol.revocationnotificaiton

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

final case class RevocationNotification(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = RevocationNotification.`type`,
    body: RevocationNotification.Body,
    thid: Option[String] = None,
    from: DidId,
    to: DidId,
) {
  assert(`type` == RevocationNotification.`type`)

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get,
  )
}
object RevocationNotification {

  given JsonEncoder[RevocationNotification] = DeriveJsonEncoder.gen
  given JsonDecoder[RevocationNotification] = DeriveJsonDecoder.gen

  def `type`: PIURI = "https://atalaprism.io/revocation_notification/1.0/revoke"

  def build(
      fromDID: DidId,
      toDID: DidId,
      thid: Option[String] = None,
      issueCredentialProtocolThreadId: String
  ): RevocationNotification = {
    RevocationNotification(
      thid = thid,
      from = fromDID,
      to = toDID,
      body = Body(
        issueCredentialProtocolThreadId = issueCredentialProtocolThreadId,
        comment = Some("Thread Id used to issue this credential withing issue credential protocol")
      ),
    )
  }

  final case class Body(
      issueCredentialProtocolThreadId: String,
      comment: Option[String] = None,
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def readFromMessage(message: Message): Either[String, RevocationNotification] =
    message.body.as[RevocationNotification.Body] match
      case Left(err)   => Left("Fail to parse RevocationNotification's body: " + err)
      case Right(body) =>
        message.from match
          case None       => Left("OfferCredential MUST have the sender explicit")
          case Some(from) =>
            message.to match
              case firstTo +: Seq() =>
                Right(
                  RevocationNotification(
                    id = message.id,
                    `type` = message.piuri,
                    body = body,
                    thid = message.thid,
                    from = from,
                    to = firstTo,
                  )
                )
              case tos => Left(s"OfferCredential MUST have only 1 recipient instead has '${tos}'")

}
