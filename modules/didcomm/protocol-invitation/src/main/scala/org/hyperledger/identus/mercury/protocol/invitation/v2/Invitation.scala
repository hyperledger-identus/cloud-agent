package org.hyperledger.identus.mercury.protocol.invitation.v2

import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId, PIURI}
import org.hyperledger.identus.mercury.model.AttachmentDescriptor.attachmentDescriptorEncoderV2
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

/** Out-Of-Band invitation
  *
  * @see
  *   https://identity.foundation/didcomm-messaging/spec/#invitation
  */
final case class Invitation(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = Invitation.`type`,
    from: DidId,
    body: Invitation.Body,
    attachments: Option[Seq[AttachmentDescriptor]] = None,
    created_time: Option[Long] = None,
    expires_time: Option[Long] = None,
) {
  assert(`type` == "https://didcomm.org/out-of-band/2.0/invitation")
  def toBase64: String = java.util.Base64.getUrlEncoder.encodeToString(this.toJson.getBytes)
}

object Invitation {

  final case class Body(
      goal_code: Option[String],
      goal: Option[String],
      accept: Seq[String]
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def `type`: PIURI = "https://didcomm.org/out-of-band/2.0/invitation"
  given JsonEncoder[Invitation] = DeriveJsonEncoder.gen
  given JsonDecoder[Invitation] = DeriveJsonDecoder.gen

}
