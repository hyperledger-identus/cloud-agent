package org.hyperledger.identus.mercury.protocol.invitation.v1
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, PIURI}
import org.hyperledger.identus.mercury.model.AttachmentDescriptor.attachmentDescriptorEncoderV1
import org.hyperledger.identus.mercury.protocol.invitation.ServiceType
import zio.json.{DeriveJsonDecoder, JsonDecoder, JsonEncoder}
import zio.json.internal.Write

/** Out-Of-Band invitation Example
  *
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0434-outofband
  * @param `id`
  * @param label
  * @param goal
  * @param goal_code
  * @param handshake_protocols
  * @param `request~attach`
  * @param services
  */
final case class Invitation(
    `@id`: String = org.hyperledger.identus.mercury.protocol.invitation.getNewMsgId,
    label: String,
    goal: String,
    goal_code: String,
    accept: Seq[String],
    handshake_protocols: Seq[String],
    `requests~attach`: Seq[AttachmentDescriptor],
    services: Seq[ServiceType]
) {
  val `@type`: PIURI = "https://didcomm.org/out-of-band/2.0/invitation"
}

object Invitation {
  given JsonEncoder[Invitation] = (i: Invitation, indent: Option[Int], out: Write) => {
    out.write("{")
    out.write("\"@id\":")
    JsonEncoder[String].unsafeEncode(i.`@id`, indent, out)
    out.write(",\"@type\":")
    JsonEncoder[String].unsafeEncode(i.`@type`, indent, out)
    out.write(",\"label\":")
    JsonEncoder[String].unsafeEncode(i.label, indent, out)
    out.write(",\"goal\":")
    JsonEncoder[String].unsafeEncode(i.goal, indent, out)
    out.write(",\"goal_code\":")
    JsonEncoder[String].unsafeEncode(i.goal_code, indent, out)
    out.write(",\"accept\":")
    JsonEncoder[Seq[String]].unsafeEncode(i.accept, indent, out)
    out.write(",\"handshake_protocols\":")
    JsonEncoder[Seq[String]].unsafeEncode(i.handshake_protocols, indent, out)
    out.write(",\"requests~attach\":")
    JsonEncoder[Seq[AttachmentDescriptor]].unsafeEncode(i.`requests~attach`, indent, out)
    out.write(",\"services\":")
    JsonEncoder[Seq[ServiceType]].unsafeEncode(i.services, indent, out)
    out.write("}")
  }
  given JsonDecoder[Invitation] = DeriveJsonDecoder.gen
}
