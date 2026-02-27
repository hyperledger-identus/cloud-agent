package org.hyperledger.identus.mercury

import org.hyperledger.identus.mercury.protocol.invitation.*
import org.hyperledger.identus.mercury.protocol.invitation.v2.*
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation.Body
import zio.*
import zio.json.EncoderOps

object InvitationPrograms {

  def getInvitationProgram(url: String) = for {
    _ <- ZIO.log("#### Get Invitation  ####")
    client <- ZIO.service[HttpClient]
    res <- client.get(url = url)
    message = OutOfBand.parseInvitation(res.bodyAsString)
    _ <- ZIO.log(s"*******OutOfBand********${message.toString}")
  } yield (message)

  def createInvitationV2(): ZIO[DidOps & DidAgent, Nothing, String] = {
    for {
      merdiator <- ZIO.service[DidAgent]
      // _ <- ZIO.unit
      invitation = Invitation(
        "https://didcomm.org/out-of-band/2.0/invitation",
        getNewMsgId,
        merdiator.id,
        Body(Some("request-mediate"), Some("RequestMediate"), Seq("didcomm/v2", "didcomm/aip2;env=rfc587"))
      )
      _ <- ZIO.log(s"createInvitationV2 from '${merdiator.id}'")
      result = invitation.toJson
    } yield (java.util.Base64.getUrlEncoder.encodeToString(result.toJson.getBytes))

  }

}
