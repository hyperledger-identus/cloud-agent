package org.hyperledger.identus.mercury.protocol.invitation
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.internal.Write

sealed trait ServiceType

object ServiceType {
  given JsonEncoder[ServiceType] = (a: ServiceType, indent: Option[Int], out: Write) => {
    a match
      case data @ Did(did)               => JsonEncoder[String].unsafeEncode(did, indent, out)
      case data @ Service(_, _, _, _, _) => JsonEncoder[Service].unsafeEncode(data, indent, out)
  }
  given JsonDecoder[ServiceType] = DeriveJsonDecoder.gen
}

/** Service block
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0434-outofband
  * @param id
  * @param `type`
  * @param recipientKeys
  * @param routingKeys
  * @param serviceEndpoint
  */
case class Service(
    id: String,
    `type`: String,
    recipientKeys: Seq[String],
    routingKeys: Option[Seq[String]],
    serviceEndpoint: String,
) extends ServiceType

object Service {
  given JsonEncoder[Service] = DeriveJsonEncoder.gen
  given JsonDecoder[Service] = DeriveJsonDecoder.gen
}

case class Did(did: String) extends ServiceType

object Did {
  given JsonEncoder[Did] = DeriveJsonEncoder.gen
  given JsonDecoder[Did] = DeriveJsonDecoder.gen
}
