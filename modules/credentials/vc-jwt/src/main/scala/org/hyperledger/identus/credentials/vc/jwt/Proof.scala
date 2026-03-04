package org.hyperledger.identus.credentials.vc.jwt

import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.SignedJWT
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Ed25519PublicKey, KmpEd25519KeyOps}
import org.hyperledger.identus.shared.json.Json as JsonUtils
import org.hyperledger.identus.shared.utils.Base64Utils
import scodec.bits.ByteVector
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

import java.io.IOException
import java.security.interfaces.ECPublicKey
import java.time.Instant
import scala.jdk.CollectionConverters.*

object EcdsaSecp256k1Signature2019ProofGenerator {
  private def stripLeadingZero(arr: Array[Byte]): Array[Byte] = {
    if (arr.length == 33 && arr.head == 0) then arr.tail else arr
  }
  def generateProof(payload: Json, signer: ECDSASigner, pk: ECPublicKey): Task[EcdsaSecp256k1Signature2019Proof] = {
    for {
      dataToSign <- ZIO.fromEither(JsonUtils.canonicalizeJsonLDoRdf(payload.toJson))
      created = Instant.now()
      header = new JWSHeader.Builder(JWSAlgorithm.ES256K)
        .base64URLEncodePayload(false)
        .criticalParams(Set("b64").asJava)
        .build()
      payload = Payload(dataToSign)
      jwsObject = JWSObject(header, payload)
      _ = jwsObject.sign(signer)
      jws = jwsObject.serialize(true)
      x = stripLeadingZero(pk.getW.getAffineX.toByteArray)
      y = stripLeadingZero(pk.getW.getAffineY.toByteArray)
      jwk = JsonWebKey(
        kty = "EC",
        crv = Some("secp256k1"),
        key_ops = Vector("verify"),
        x = Some(Base64Utils.encodeURL(x)),
        y = Some(Base64Utils.encodeURL(y)),
      )
      ecdaSecp256k1VerificationKey2019 = EcdsaSecp256k1VerificationKey2019(
        publicKeyJwk = jwk
      )
      verificationMethodUrl = Base64Utils.createDataUrl(
        ecdaSecp256k1VerificationKey2019.toJson.getBytes,
        "application/json"
      )
    } yield EcdsaSecp256k1Signature2019Proof(
      jws = jws,
      verificationMethod = verificationMethodUrl,
      created = Some(created),
    )
  }

  def verifyProof(payload: Json, jws: String, pk: ECPublicKey): Task[Boolean] = {
    for {
      dataToVerify <- ZIO.fromEither(JsonUtils.canonicalizeJsonLDoRdf(payload.toJson))
      verifier = JWTVerification.toECDSAVerifier(pk)
      signedJws = SignedJWT.parse(jws)
      header = signedJws.getHeader
      signature = signedJws.getSignature
      payload = Payload(dataToVerify)
      jwsObject = new SignedJWT(header.toBase64URL, payload.toBase64URL, signature)
      isValid = jwsObject.verify(verifier)
    } yield isValid
  }
}

object EddsaJcs2022ProofGenerator {
  private val ed25519MultiBaseHeader: Array[Byte] = Array(-19, 1) // 0xed01

  private def pkToMultiKey(pk: Ed25519PublicKey): MultiKey = {
    val encoded = pk.getEncoded
    val withHeader = ed25519MultiBaseHeader ++ encoded
    val base58Encoded = ByteVector.view(withHeader).toBase58
    MultiKey(publicKeyMultibase =
      Some(
        MultiBaseString(
          header = MultiBaseString.Header.Base58Btc,
          data = base58Encoded
        )
      )
    )
  }

  private def multiKeytoPk(multiKey: MultiKey): Either[String, Ed25519PublicKey] = {
    for {
      multiBaseStr <- multiKey.publicKeyMultibase.toRight("No public key provided inside MultiKey")
      bytesWithHeader <- multiBaseStr.getBytes
      pkBytes <- Either.cond(
        bytesWithHeader.take(2).sameElements(ed25519MultiBaseHeader),
        bytesWithHeader.drop(2),
        "Invalid multiBaseString header for ed25519"
      )
      maybePk <- Either.cond(
        pkBytes.length == 32,
        KmpEd25519KeyOps.publicKeyFromEncoded(pkBytes),
        "Invalid public key length, must be 32"
      )
      pk <- maybePk.toEither.left.map(_.getMessage)

    } yield pk
  }

  def generateProof(payload: Json, ed25519KeyPair: Ed25519KeyPair): Task[EddsaJcs2022Proof] = {
    for {
      canonicalizedJsonString <- ZIO.fromEither(JsonUtils.canonicalizeToJcs(payload.toJson))
      canonicalizedJson <- ZIO.fromEither(canonicalizedJsonString.fromJson[Json].left.map(e => IOException(e)))
      dataToSign = canonicalizedJson.toJson.getBytes
      signature = ed25519KeyPair.privateKey.sign(dataToSign)
      base58BtsEncodedSignature = MultiBaseString(
        header = MultiBaseString.Header.Base58Btc,
        data = ByteVector.view(signature).toBase58
      ).toMultiBaseString
      created = Instant.now()
      multiKey = pkToMultiKey(ed25519KeyPair.publicKey)
      verificationMethod = Base64Utils.createDataUrl(
        multiKey.toJson.getBytes,
        "application/json"
      )
    } yield EddsaJcs2022Proof(
      proofValue = base58BtsEncodedSignature,
      maybeCreated = Some(created),
      verificationMethod = verificationMethod
    )
  }

  def verifyProof(payload: Json, proofValue: String, pk: MultiKey): IO[IOException, Boolean] = for {
    canonicalizedJsonString <- ZIO.fromEither(JsonUtils.canonicalizeToJcs(payload.toJson))
    canonicalizedJson <- ZIO.fromEither(canonicalizedJsonString.fromJson[Json].left.map(e => IOException(e)))
    dataToVerify = canonicalizedJson.toJson.getBytes
    signature <- ZIO
      .fromEither(MultiBaseString.fromString(proofValue).flatMap(_.getBytes))
      .mapError(error => IOException(error))
    kmmPk <- ZIO
      .fromEither(multiKeytoPk(pk))
      .mapError(error => IOException(error))

    isValid = verify(kmmPk, signature, dataToVerify)
  } yield isValid

  private def verify(publicKey: Ed25519PublicKey, signature: Array[Byte], data: Array[Byte]): Boolean = {
    publicKey.verify(data, signature).isSuccess
  }
}
