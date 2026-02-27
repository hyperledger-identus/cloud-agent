package org.hyperledger.identus.mercury.protocol.connection

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

object ConnectionResponse {
  def `type`: PIURI = "https://atalaprism.io/mercury/connections/1.0/response"

  final case class Body(
      goal_code: Option[String] = None,
      goal: Option[String] = None,
      accept: Option[Seq[String]] = None
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def makeResponseFromRequest(msg: Message): Either[String, ConnectionResponse] =
    for {
      cr: ConnectionRequest <- ConnectionRequest.fromMessage(msg)
      ret = makeResponseFromRequest(cr)
    } yield (ret)

  def makeResponseFromRequest(cr: ConnectionRequest): ConnectionResponse =
    ConnectionResponse(
      body = ConnectionResponse.Body(
        goal_code = cr.body.goal_code,
        goal = cr.body.goal,
        accept = cr.body.accept,
      ),
      thid = cr.thid.orElse(Some(cr.id)),
      pthid = cr.pthid,
      from = cr.to,
      to = cr.from,
    )

  /** Parse a generecy DIDComm Message into a ConnectionResponse */
  def fromMessage(message: Message): Either[String, ConnectionResponse] =
    for {
      piuri <-
        if (message.`type` == ConnectionResponse.`type`) Right(message.`type`)
        else Left(s"Message MUST be of the type '${ConnectionResponse.`type`}' instead of '${message.`type`}'")
      body <- message.body
        .as[ConnectionResponse.Body]
        .left
        .map(err => "Fail to parse the body of the ConnectionResponse because: " + err)
      ret <- message.to match
        case Seq(inviter) => // is from only one inviter
          message.from match
            case Some(invitee) =>
              Right(
                ConnectionResponse(
                  id = message.id,
                  `type` = piuri,
                  body = body,
                  thid = message.thid,
                  pthid = message.pthid,
                  from = invitee, // TODO get
                  to = inviter
                )
              )
            case None => Left("ConnectionResponse needs to define the Inviter")
        case _ => Left("The inviter (recipient) is ambiguous. Message need to have only 1 recipient")
    } yield ret

  given JsonEncoder[ConnectionResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[ConnectionResponse] = DeriveJsonDecoder.gen
}

final case class ConnectionResponse(
    `type`: PIURI = ConnectionResponse.`type`,
    id: String = java.util.UUID.randomUUID().toString,
    from: DidId,
    to: DidId,
    thid: Option[String],
    pthid: Option[String],
    body: ConnectionResponse.Body,
) {
  assert(`type` == "https://atalaprism.io/mercury/connections/1.0/response")

  def makeMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    pthid = this.pthid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).getOrElse(Json.Obj()),
  )
}
