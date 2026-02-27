package org.hyperledger.identus.mercury

import com.nimbusds.jose.jwk.OctetKeyPair
import org.didcommx.didcomm.common.*
import org.didcommx.didcomm.secret.*
import org.didcommx.peerdid.core.PeerDIDUtils
import org.hyperledger.identus.mercury.model.*
import zio.*

import scala.jdk.CollectionConverters.*

case class AgentPeerService(
    val id: DidId,
    val jwkForKeyAgreement: Seq[OctetKeyPair],
    val jwkForKeyAuthentication: Seq[OctetKeyPair],
) extends DidAgent

object AgentPeerService {
  def makeLayer(peer: PeerDID): ZLayer[Any, Nothing, DidAgent] = ZLayer.succeed(
    AgentPeerService(
      id = peer.did,
      jwkForKeyAgreement = Seq(peer.jwkForKeyAgreement),
      jwkForKeyAuthentication = Seq(peer.jwkForKeyAuthentication),
    )
  )

  def getSecretResolverInMemory(agent: DidAgent): SecretResolverInMemory = {
    val keyAgreement = PeerDID.keyAgreemenFromPublicJWK(agent.jwkForKeyAgreement.head) // TODO Fix head
    val keyAuthentication = PeerDID.keyAuthenticationFromPublicJWK(agent.jwkForKeyAuthentication.head) // TODO Fix head

    val keyIdAgreement = PeerDIDUtils.createMultibaseEncnumbasis(keyAgreement).drop(1)
    val keyIdAuthentication = PeerDIDUtils.createMultibaseEncnumbasis(keyAuthentication).drop(1)
    val keyIdAgreementIndex = agent.id.value.indexOf(keyIdAgreement)
    val keyIdAuthenticationIndex = agent.id.value.indexOf(keyIdAuthentication)
    val (keyAgreementId, keyAuthenticationId) =
      if keyIdAgreementIndex < keyIdAuthenticationIndex then (1, 2) else (2, 1)

    val secretKeyAgreement = new Secret(
      s"${agent.id.value}#key-$keyAgreementId",
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(VerificationMaterialFormat.JWK, agent.jwkForKeyAgreement.head.toJSONString)
    )
    val secretKeyAuthentication = new Secret(
      s"${agent.id.value}#key-$keyAuthenticationId",
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(VerificationMaterialFormat.JWK, agent.jwkForKeyAuthentication.head.toJSONString)
    )

    new SecretResolverInMemory(
      Map(
        s"${agent.id.value}#key-$keyAgreementId" -> secretKeyAgreement,
        s"${agent.id.value}#key-$keyAuthenticationId" -> secretKeyAuthentication,
      ).asJava
    )
  }
}
