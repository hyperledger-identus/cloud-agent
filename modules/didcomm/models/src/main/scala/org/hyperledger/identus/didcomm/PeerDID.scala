package org.hyperledger.identus.didcomm

import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.gen.*
import org.hyperledger.identus.didcomm.model.DidId
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class PeerDID(
    did: DidId,
    jwkForKeyAgreement: OctetKeyPair,
    jwkForKeyAuthentication: OctetKeyPair,
)

object PeerDID {

  /** PeerDidServiceEndpoint
    *
    * @param r
    *   routingKeys are OPTIONAL. An ordered array of strings referencing keys to be used when preparing the message for
    *   transmission as specified in Sender Process to Enable Forwarding, above.
    */

  case class ServiceEndpoint(uri: String, r: Seq[String] = Seq.empty, a: Seq[String] = Seq("didcomm/v2"))
  object ServiceEndpoint {
    implicit val encoder: JsonEncoder[ServiceEndpoint] = DeriveJsonEncoder.gen
    implicit val decoder: JsonDecoder[ServiceEndpoint] = DeriveJsonDecoder.gen
    def apply(endpoint: String) = new ServiceEndpoint(uri = endpoint)
  }

  case class Service(
      t: String = "dm",
      s: ServiceEndpoint
  ) {
    def `type` = t
    def serviceEndpoint = s
    def routingKeys = s.r
    def accept = s.a
  }
  object Service {
    implicit val encoder: JsonEncoder[Service] = DeriveJsonEncoder.gen
    implicit val decoder: JsonDecoder[Service] = DeriveJsonDecoder.gen
    def apply(endpoint: String) = new Service(s = ServiceEndpoint(endpoint))
  }

  def makeNewJwkKeyX25519: OctetKeyPair = new OctetKeyPairGenerator(Curve.X25519).generate()

  def makeNewJwkKeyEd25519: OctetKeyPair = new OctetKeyPairGenerator(Curve.Ed25519).generate()
}
