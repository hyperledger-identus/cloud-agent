package org.hyperledger.identus.mercury

import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.gen.*
import org.didcommx.peerdid.*
import org.hyperledger.identus.mercury.model.DidId
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

import scala.jdk.CollectionConverters.*

final case class PeerDID(
    did: DidId,
    jwkForKeyAgreement: OctetKeyPair,
    jwkForKeyAuthentication: OctetKeyPair,
) {
  // def keyAgreement = PeerDID.keyAgreemenFromPublicJWK(jwkForKeyAgreement)
  // def keyAuthentication = PeerDID.keyAuthenticationFromPublicJWK(jwkForKeyAuthentication)

  // def getSecretResolverInMemory: SecretResolverInMemory = {
  //   val keyIdAgreement = PeerDIDUtils.createMultibaseEncnumbasis(keyAgreement).drop(1)
  //   val keyIdAuthentication = PeerDIDUtils.createMultibaseEncnumbasis(keyAuthentication).drop(1)

  //   val secretKeyAgreement = new Secret(
  //     s"${did.value}#$keyIdAgreement",
  //     VerificationMethodType.JSON_WEB_KEY_2020,
  //     new VerificationMaterial(VerificationMaterialFormat.JWK, jwkForKeyAgreement.toJSONString)
  //   )
  //   val secretKeyAuthentication = new Secret(
  //     s"${did.value}#$keyIdAuthentication",
  //     VerificationMethodType.JSON_WEB_KEY_2020,
  //     new VerificationMaterial(VerificationMaterialFormat.JWK, jwkForKeyAuthentication.toJSONString)
  //   )

  //   new SecretResolverInMemory(
  //     Map(
  //       s"${did.value}#$keyIdAgreement" -> secretKeyAgreement,
  //       s"${did.value}#$keyIdAuthentication" -> secretKeyAuthentication,
  //     ).asJava
  //   )
  // }

  def getDIDDocument = org.didcommx.peerdid.PeerDIDResolver
    .resolvePeerDID(did.value, VerificationMaterialFormatPeerDID.JWK)
}

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

  def keyAgreemenFromPublicJWK(key: OctetKeyPair) = VerificationMaterialPeerDID[VerificationMethodTypeAgreement](
    VerificationMaterialFormatPeerDID.JWK,
    key.toPublicJWK,
    VerificationMethodTypeAgreement.JSON_WEB_KEY_2020.INSTANCE
  )

  def keyAuthenticationFromPublicJWK(key: OctetKeyPair) =
    VerificationMaterialPeerDID[VerificationMethodTypeAuthentication](
      VerificationMaterialFormatPeerDID.JWK,
      key.toPublicJWK,
      VerificationMethodTypeAuthentication.JSON_WEB_KEY_2020.INSTANCE
    )

  def makePeerDid(
      jwkForKeyAgreement: OctetKeyPair = makeNewJwkKeyX25519,
      jwkForKeyAuthentication: OctetKeyPair = makeNewJwkKeyEd25519,
      serviceEndpoint: Option[String] = None
  ): PeerDID = {
    val did = org.didcommx.peerdid.PeerDIDCreator.createPeerDIDNumalgo2(
      List(keyAgreemenFromPublicJWK(jwkForKeyAgreement)).asJava,
      List(keyAuthenticationFromPublicJWK(jwkForKeyAuthentication)).asJava,
      serviceEndpoint match {
        case Some(endpoint) => Service(endpoint).toJson
        case None           => null
      }
    )
    PeerDID(DidId(did), jwkForKeyAgreement, jwkForKeyAuthentication)
  }
}
