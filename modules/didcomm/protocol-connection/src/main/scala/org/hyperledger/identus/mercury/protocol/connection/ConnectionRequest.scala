package org.hyperledger.identus.mercury.protocol.connection

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

object ConnectionRequest {
  def `type`: PIURI = "https://atalaprism.io/mercury/connections/1.0/request"

  case class Body(
      goal_code: Option[String] = None,
      goal: Option[String] = None,
      accept: Option[Seq[String]] = None
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen[Body]
    given JsonDecoder[Body] = DeriveJsonDecoder.gen[Body]
  }

  given JsonEncoder[ConnectionRequest] = DeriveJsonEncoder.gen[ConnectionRequest]
  given JsonDecoder[ConnectionRequest] = DeriveJsonDecoder.gen[ConnectionRequest]

  /** Utility method to start a ConnectionRequest for the scenario where he has an OOB Invitation
    *
    * @see
    *   [[ConnectionInvitation.makeConnectionRequest]]
    */
  def makeFromInvitation(invitation: Invitation, invitee: DidId): ConnectionRequest =
    ConnectionRequest(
      `type` = ConnectionRequest.`type`,
      body = Body(
        goal_code = invitation.body.goal_code,
        goal = invitation.body.goal,
        accept = Some(invitation.body.accept)
      ),
      thid = None,
      pthid = Some(invitation.id),
      from = invitee,
      to = invitation.from
    )

  /** Parse a generecy DIDComm Message into a ConnectionRequest */
  def fromMessage(message: Message): Either[String, ConnectionRequest] =
    for {
      piuri <-
        if (message.`type` == ConnectionRequest.`type`) Right(message.`type`)
        else Left(s"Message MUST be of the type '${ConnectionRequest.`type`}' instead of '${message.`type`}'")
      body <- message.body
        .as[ConnectionRequest.Body]
        .left
        .map(err => "Fail to parse the body of the ConnectionRequest because: " + err)
      ret <- message.to match
        case Seq(onlyOneRecipient) =>
          message.from match
            case Some(inviter) =>
              Right(
                ConnectionRequest(
                  id = message.id,
                  `type` = piuri,
                  body = body,
                  thid = message.thid,
                  pthid = message.pthid,
                  from = inviter,
                  to = onlyOneRecipient
                )
              )
            case None => Left("ConnectionRequest needs to define the Inviter")
        case _ => Left("The recipient is ambiguous. Need to have only 1 recipient")
    } yield ret

}

final case class ConnectionRequest(
    `type`: PIURI = ConnectionRequest.`type`,
    id: String = java.util.UUID.randomUUID().toString,
    from: DidId,
    to: DidId,
    thid: Option[String],
    pthid: Option[String],
    body: ConnectionRequest.Body,
) {
  assert(`type` == "https://atalaprism.io/mercury/connections/1.0/request")

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
