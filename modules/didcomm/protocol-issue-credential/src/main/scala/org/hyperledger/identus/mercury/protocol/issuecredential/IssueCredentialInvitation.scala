package org.hyperledger.identus.mercury.protocol.issuecredential

import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId}
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import zio.Duration

import java.time.Instant

object IssueCredentialInvitation {
  def makeInvitation(
      from: DidId,
      goalCode: Option[String],
      goal: Option[String],
      invitationId: String,
      offerCredential: OfferCredential,
      expirationDuration: Option[Duration] = None,
  ): Invitation = {
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(payload = offerCredential)
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
