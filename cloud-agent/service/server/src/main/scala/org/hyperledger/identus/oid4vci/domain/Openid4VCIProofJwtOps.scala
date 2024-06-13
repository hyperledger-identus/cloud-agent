package org.hyperledger.identus.oid4vci.domain

import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader, JWSObject, JWSSigner, Payload}
import org.hyperledger.identus.castor.core.model.did.LongFormPrismDID
import org.hyperledger.identus.pollux.vc.jwt.{DidResolver, JWT}
import org.hyperledger.identus.pollux.vc.jwt.JwtSignerImplicits.*
import org.hyperledger.identus.shared.crypto.Secp256k1PrivateKey
import zio.Task
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Try

trait Openid4VCIProofJwtOps {

  val jwtTypeName = "openid4vci-proof+jwt"

  val supportedAlgorithms: Set[String] = Set("ES256K")

  private def buildHeaderFromLongFormDid(longFormDID: LongFormPrismDID) = {
    new JWSHeader.Builder(JWSAlgorithm.ES256K)
      .keyID(longFormDID.toString)
      .`type`(new JOSEObjectType(jwtTypeName))
      .build()
  }

  private def buildPayload(nonce: String, aud: UUID, iat: Int) = {
    new Payload(Map("nonce" -> nonce, "aud" -> aud.toString, "iat" -> iat).asJava)
  }

  private def makeJwtProof(header: JWSHeader, payload: Payload, signer: JWSSigner): String = {
    val jwt = new JWSObject(header, payload)
    jwt.sign(signer)
    jwt.serialize()
  }

  def makeJwtProof(
      longFormPrismDID: LongFormPrismDID,
      nonce: String,
      aud: UUID,
      iat: Int,
      privateKey: Secp256k1PrivateKey
  ): JWT = {
    val header = buildHeaderFromLongFormDid(longFormPrismDID)
    val payload = buildPayload(nonce, aud, iat)
    JWT(makeJwtProof(header, payload, privateKey.asJwtSigner))
  }

  def getKeyIdFromJwt(jwt: JWT): Task[String] = {
    for {
      jwsObject <- ZIO.fromTry(Try(JWSObject.parse(jwt.value)))
      keyID = jwsObject.getHeader.getKeyID
      _ <- ZIO.fail(new Exception("Key ID not found in JWT header")) when (keyID == null || keyID.isEmpty)
    } yield keyID
  }

  def getNonceFromJwt(jwt: JWT): Task[String] = {
    for {
      jwsObject <- ZIO.fromTry(Try(JWSObject.parse(jwt.value)))
      payload = jwsObject.getPayload.toJSONObject
      nonce = payload.get("nonce").asInstanceOf[String]
      _ <- ZIO.fail(new Exception("Nonce not found in JWT payload")) when (nonce == null || nonce.isEmpty)
    } yield nonce
  }
}
