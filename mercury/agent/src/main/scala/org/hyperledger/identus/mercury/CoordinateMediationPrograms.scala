package org.hyperledger.identus.mercury

import org.hyperledger.identus.mercury.model.*
import org.hyperledger.identus.mercury.protocol.coordinatemediation.*
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

object CoordinateMediationPrograms {

  def replyToInvitation(replier: DidId, invitation: Invitation) = {
    val requestMediation = MediateRequest()
    Message(
      from = Some(replier),
      to = Seq(invitation.from),
      id = requestMediation.id,
      `type` = requestMediation.`type`
    )
  }

  private def toPrettyJson(parseToJson: String): Either[String, String] = {
    parseToJson.fromJson[Json].map(_.toJsonPretty)
  }

  def senderMediationRequestProgram(mediatorURL: String = "http://localhost:8000") = {

    for {
      _ <- ZIO.log("#### Send Mediation request ####")
      link <- InvitationPrograms
        .getInvitationProgram(mediatorURL + "/oob_url")
        .flatMap {
          case Left(value)  => ZIO.fail(value)
          case Right(value) => ZIO.succeed(value)
        }
      opsService <- ZIO.service[DidOps]
      agentService <- ZIO.service[DidAgent]

      planMessage = replyToInvitation(agentService.id, link)
      invitationFrom = link.from
      _ <- ZIO.log(s"Invitation from $invitationFrom")

      encryptedMessage <- opsService.packEncrypted(planMessage, to = invitationFrom)
      _ <- ZIO.log("Sending bytes ...")
      jsonString = encryptedMessage.string
      _ <- ZIO.log(jsonString)

      client <- ZIO.service[HttpClient]
      res <- client.postDIDComm(mediatorURL, jsonString)
      _ <- ZIO.log(res.bodyAsString)

      messageReceived <- opsService.unpack(res.bodyAsString)
      _ <- Console.printLine("Unpacking and decrypting the received message ...")
      _ <- Console.printLine("*" * 100)
      tmp <- ZIO.fromEither(toPrettyJson(messageReceived.message.toString))
      _ <- Console.printLine(tmp)
      _ <- Console.printLine("*" * 100)
      ret <- ZIO
        .fromEither(messageReceived.message.toString.fromJson[Json])
        .flatMap { json =>
          json.as[MediateGrant] match {
            case Right(mediateGrant) => ZIO.succeed(mediateGrant)
            case Left(_)             =>
              json.as[MediateDeny] match {
                case Right(mediateDeny) => ZIO.succeed(mediateDeny)
                case Left(ex)           => ZIO.fail(ex)
              }
          }
        }
      _ <- Console.printLine(ret)
      _ <- Console.printLine("*" * 100)
    } yield (ret)
  }

}
