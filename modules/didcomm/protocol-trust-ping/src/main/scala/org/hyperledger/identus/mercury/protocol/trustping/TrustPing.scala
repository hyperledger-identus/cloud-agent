package org.hyperledger.identus.mercury.protocol.trustping

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

/** https://identity.foundation/didcomm-messaging/spec/#trust-ping-protocol-20 */
final case class TrustPing(
    `type`: PIURI = TrustPing.`type`,
    id: String = java.util.UUID.randomUUID().toString,
    from: DidId,
    to: DidId,
    body: TrustPing.Body,
) {
  assert(`type` == TrustPing.`type`)

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get,
  )

  def makeReply = TrustPingResponse(
    thid = this.id,
    from = to,
    to = from,
  )
}

object TrustPing {
  def `type`: PIURI = "https://didcomm.org/trust-ping/2.0/ping"
  case class Body(
      response_requested: Option[Boolean] = Some(true),
  )
  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }
  given JsonEncoder[TrustPing] = DeriveJsonEncoder.gen
  given JsonDecoder[TrustPing] = DeriveJsonDecoder.gen

  /** Parse a generecy DIDComm Message into a TrustPing */
  def fromMessage(message: Message): Either[String, TrustPing] =
    for {
      piuri <-
        if (message.`type` == TrustPing.`type`) Right(message.`type`)
        else Left(s"Message MUST be of the type '${TrustPing.`type`}' instead of '${message.`type`}'")
      body <- message.body
        .as[TrustPing.Body]
        .left
        .map(err => "Fail to parse the body of the TrustPing because: " + err)
      ret <- message.to match
        case Seq(onlyOneRecipient) =>
          message.from match
            case Some(from) =>
              Right(
                TrustPing(
                  id = message.id,
                  `type` = piuri,
                  body = body,
                  from = from,
                  to = onlyOneRecipient
                )
              )
            case None => Left("TrustPing needs to define the recipient")
        case _ => Left("The recipient is ambiguous. Need to have only 1 recipient")
    } yield ret

}
