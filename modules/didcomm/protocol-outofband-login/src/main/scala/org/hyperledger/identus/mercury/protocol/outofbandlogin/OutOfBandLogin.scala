package org.hyperledger.identus.mercury.protocol.outofbandlogin

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}

/** Out-Of-Band Login Invitation
  * @see
  *   https://atalaprism.io/mercury/outofband-login/1.0/invitation
  */
final case class OutOfBandLoginInvitation(
    `type`: PIURI = OutOfBandLoginInvitation.piuri,
    id: String = Utils.getNewMsgId,
    from: DidId
) {
  assert(`type` == OutOfBandLoginInvitation.piuri)

  def makeMsg: Message = Message(`type` = `type`, id = id, from = Some(from), to = Seq.empty)

  def reply(replier: DidId) = OutOfBandloginReply(
    from = replier,
    to = from,
    thid = id, // Thread identifier. Uniquely identifies the thread that the message belongs to
  )

}

object OutOfBandLoginInvitation {
  def piuri: PIURI = "https://atalaprism.io/mercury/outofband-login/1.0/invitation"
}

/** Reply to Login Invitation
  * @see
  *   https://atalaprism.io/mercury/outofband-login/1.0/reply
  */
final case class OutOfBandloginReply(
    `type`: PIURI = OutOfBandloginReply.piuri,
    id: String = Utils.getNewMsgId,
    from: DidId,
    to: DidId,
    thid: String,
    // replyTo: OutOfBandLoginInvitation
) {
  assert(`type` == OutOfBandloginReply.piuri)

  def makeMsg: Message = Message(`type` = `type`, id = id, from = Some(from), to = Seq(to), thid = Some(thid))
}

object OutOfBandloginReply {
  def piuri: PIURI = "https://atalaprism.io/mercury/outofband-login/1.0/reply"
}
