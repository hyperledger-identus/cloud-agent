package org.hyperledger.identus.didcomm.protocol.presentproof

import org.hyperledger.identus.didcomm.model.{AttachmentDescriptor, DidId}
import org.hyperledger.identus.didcomm.protocol.invitation.v2.Invitation
import zio.Duration

import java.time.Instant

object PresentProofInvitation {
  def makeInvitation(
      from: DidId,
      goalCode: Option[String],
      goal: Option[String],
      invitationId: String,
      requestPresentation: RequestPresentation,
      expirationDuration: Option[Duration] = None,
  ): Invitation = {
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(payload = requestPresentation)
    val now = Instant.now
    Invitation(
      id = invitationId,
      from = from,
      created_time = Some(now.getEpochSecond),
      expires_time = expirationDuration.map(now.plus(_).getEpochSecond),
      body = Invitation.Body(
        goal_code = goalCode,
        goal = goal,
        accept = Seq("didcomm/v2")
      ),
      attachments = Some(Seq(attachmentDescriptor))
    )
  }

}
