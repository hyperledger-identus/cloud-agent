package org.hyperledger.identus.didcomm

import org.hyperledger.identus.didcomm.model.*
import org.hyperledger.identus.didcomm.protocol.outofbandlogin.*

object OutOfBandLoginPrograms {

  def generateLoginInvitation(did: DidId) = {
    val invitation = OutOfBandLoginInvitation(from = did)
    Message(
      `type` = invitation.`type`,
      from = Some(invitation.from),
      to = Seq.empty,
      id = invitation.id,
    )
  }

  // def replyToLoginInvitation(replier: DidId, invitation: Invitation) = {
  //   val requestMediation = MediateRequest()
  //   Message(
  //     from = replier,
  //     to = DidId(invitation.from),
  //     id = requestMediation.id,
  //     piuri = requestMediation.`type`
  //   )
  // }
}
