package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{ECDSASigner, Ed25519Signer}
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.{Curve, ECKey}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Secp256k1PrivateKey}
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.json.{EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

import java.security.*
import java.security.interfaces.ECPublicKey

opaque type JWT = String

object JWT {
  def apply(value: String): JWT = value

  extension (jwt: JWT) {
    def value: String = jwt
  }

  given JsonEncoder[JWT] = JsonEncoder.string.contramap(jwt => jwt.value)
  given JsonDecoder[JWT] = JsonDecoder.string.map(JWT(_))
}

object JwtSignerImplicits {
  import com.nimbusds.jose.JWSSigner

  implicit class JwtSignerProviderSecp256k1(secp256k1PrivateKey: Secp256k1PrivateKey) {
    def asJwtSigner: JWSSigner = {
      val ecdsaSigner = ECDSASigner(secp256k1PrivateKey.toJavaPrivateKey, Curve.SECP256K1)
      val bouncyCastleProvider = BouncyCastleProviderSingleton.getInstance
      ecdsaSigner.getJCAContext.setProvider(bouncyCastleProvider)
      ecdsaSigner
    }
  }
}

trait Signer {
  def encode(claim: Json): JWT

  def generateProofForJson(payload: Json, pk: PublicKey): Task[Proof]
}

// works with java 7, 8, 11 & bouncycastle provider
// https://connect2id.com/products/nimbus-jose-jwt/jca-algorithm-support#alg-support-table
class ES256KSigner(privateKey: PrivateKey, keyId: Option[KeyId] = None) extends Signer {
  lazy val signer: ECDSASigner = {
    val ecdsaSigner = ECDSASigner(privateKey, Curve.SECP256K1)
    val bouncyCastleProvider = BouncyCastleProviderSingleton.getInstance
    ecdsaSigner.getJCAContext.setProvider(bouncyCastleProvider)
    ecdsaSigner
  }

  override def generateProofForJson(payload: Json, pk: PublicKey): Task[Proof] = {
    val err = Throwable("Public key must be secp256k1 EC public key")
    pk match
      case pk: ECPublicKey =>
        EcdsaSecp256k1Signature2019ProofGenerator.generateProof(payload, signer, pk)
      case _ => ZIO.fail(err)
  }

  override def encode(claim: Json): JWT = {
    val claimSet = JWTClaimsSet.parse(claim.toJson)
    val signedJwt = SignedJWT(
      keyId
        .map(kid => new JWSHeader.Builder(JWSAlgorithm.ES256K).`type`(JOSEObjectType.JWT).keyID(kid.value))
        .getOrElse(new JWSHeader.Builder(JWSAlgorithm.ES256K).`type`(JOSEObjectType.JWT))
        .build(),
      claimSet
    )
    signedJwt.sign(signer)
    JWT(signedJwt.serialize())
  }
}

class EdSigner(ed25519KeyPair: Ed25519KeyPair, keyId: Option[KeyId] = None) extends Signer {
  lazy val signer: Ed25519Signer = {
    val ed25519Signer = Ed25519Signer(ed25519KeyPair.toOctetKeyPair)
    ed25519Signer
  }

  override def generateProofForJson(payload: Json, pk: PublicKey): Task[Proof] = {
    EddsaJcs2022ProofGenerator.generateProof(payload, ed25519KeyPair)
  }

  override def encode(claim: Json): JWT = {
    val claimSet = JWTClaimsSet.parse(claim.toJson)

    val signedJwt = SignedJWT(
      keyId
        .map(kid => new JWSHeader.Builder(JWSAlgorithm.EdDSA).`type`(JOSEObjectType.JWT).keyID(kid.value))
        .getOrElse(new JWSHeader.Builder(JWSAlgorithm.EdDSA).`type`(JOSEObjectType.JWT))
        .build(),
      claimSet
    )
    signedJwt.sign(signer)
    JWT(signedJwt.serialize())
  }
}

def toJWKFormat(holderJwk: ECKey): JsonWebKey = {
  JsonWebKey(
    kty = "EC",
    crv = Some(holderJwk.getCurve.getName),
    x = Some(holderJwk.getX.toJSONString),
    y = Some(holderJwk.getY.toJSONString),
    d = Some(holderJwk.getD.toJSONString)
  )
}
