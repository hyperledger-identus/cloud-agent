package org.hyperledger.identus.didcomm

import com.nimbusds.jose.jwk.OctetKeyPair
import org.didcommx.peerdid.*
import org.hyperledger.identus.didcomm.model.DidId
import zio.json.EncoderOps

import scala.jdk.CollectionConverters.*

object PeerDIDCreation {

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

  def getDIDDocument(peerDID: PeerDID) = org.didcommx.peerdid.PeerDIDResolver
    .resolvePeerDID(peerDID.did.value, VerificationMaterialFormatPeerDID.JWK)

  def makePeerDid(
      jwkForKeyAgreement: OctetKeyPair = PeerDID.makeNewJwkKeyX25519,
      jwkForKeyAuthentication: OctetKeyPair = PeerDID.makeNewJwkKeyEd25519,
      serviceEndpoint: Option[String] = None
  ): PeerDID = {
    val did = org.didcommx.peerdid.PeerDIDCreator.createPeerDIDNumalgo2(
      List(keyAgreemenFromPublicJWK(jwkForKeyAgreement)).asJava,
      List(keyAuthenticationFromPublicJWK(jwkForKeyAuthentication)).asJava,
      serviceEndpoint match {
        case Some(endpoint) => PeerDID.Service(endpoint).toJson
        case None           => null
      }
    )
    PeerDID(DidId(did), jwkForKeyAgreement, jwkForKeyAuthentication)
  }
}
