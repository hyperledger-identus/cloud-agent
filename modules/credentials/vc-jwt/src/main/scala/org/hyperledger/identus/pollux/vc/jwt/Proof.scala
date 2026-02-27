package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jose.{JWSAlgorithm, JWSHeader, JWSObject, Payload}
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.SignedJWT
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Ed25519PublicKey, KmpEd25519KeyOps}
import org.hyperledger.identus.shared.json.Json as JsonUtils
import org.hyperledger.identus.shared.utils.Base64Utils
import scodec.bits.ByteVector
import zio.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}

import java.io.IOException
import java.security.interfaces.ECPublicKey
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.jdk.CollectionConverters.*

sealed trait Proof {
  val id: Option[String] = None
  val `type`: String
  val proofPurpose: String
  val verificationMethod: String
  val created: Option[Instant] = None
  val domain: Option[String] = None
  val challenge: Option[String] = None
  val previousProof: Option[String] = None
  val nonce: Option[String] = None
}

sealed trait DataIntegrityProof extends Proof {
  val proofValue: String
}

object Proof {
  given JsonDecoder[Proof] = JsonDecoder[Json].mapOrFail { json =>
    json
      .as[EddsaJcs2022Proof]
      .orElse(json.as[EcdsaSecp256k1Signature2019Proof])
  }
}

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

case class EddsaJcs2022Proof(proofValue: String, verificationMethod: String, maybeCreated: Option[Instant])
    extends Proof
    with DataIntegrityProof {
  override val created: Option[Instant] = maybeCreated
  override val `type`: String = "DataIntegrityProof"
  override val proofPurpose: String = "assertionMethod"
  val cryptoSuite: String = "eddsa-jcs-2022"
}

object EddsaJcs2022Proof {
  given JsonEncoder[EddsaJcs2022Proof] = DataIntegrityProofCodecs.proofEncoder("eddsa-jcs-2022")
  given JsonDecoder[EddsaJcs2022Proof] = DataIntegrityProofCodecs.proofDecoder(
    (proofValue, verificationMethod, created) => EddsaJcs2022Proof(proofValue, verificationMethod, created),
    "eddsa-jcs-2022"
  )
}

case class EcdsaSecp256k1Signature2019Proof(
    jws: String,
    verificationMethod: String,
    override val created: Option[Instant] = None,
    override val challenge: Option[String] = None,
    override val domain: Option[String] = None,
    override val nonce: Option[String] = None
) extends Proof {
  override val `type`: String = "EcdsaSecp256k1Signature2019"
  override val proofPurpose: String = "assertionMethod"
}

object EcdsaSecp256k1Signature2019Proof {
  private case class Json_EcdsaSecp256k1Signature2019Proof(
      id: Option[String],
      `type`: String = "EcdsaSecp256k1Signature2019",
      proofPurpose: String = "assertionMethod",
      verificationMethod: String,
      created: Option[Instant],
      domain: Option[String],
      challenge: Option[String],
      jws: String,
      nonce: Option[String]
  )
  private object Json_EcdsaSecp256k1Signature2019Proof {
    given JsonEncoder[Json_EcdsaSecp256k1Signature2019Proof] = DeriveJsonEncoder.gen
    given JsonDecoder[Json_EcdsaSecp256k1Signature2019Proof] = DeriveJsonDecoder.gen
  }
  given JsonEncoder[EcdsaSecp256k1Signature2019Proof] = JsonEncoder[Json_EcdsaSecp256k1Signature2019Proof].contramap {
    proof =>
      Json_EcdsaSecp256k1Signature2019Proof(
        id = proof.id,
        `type` = proof.`type`,
        proofPurpose = proof.proofPurpose,
        verificationMethod = proof.verificationMethod,
        created = proof.created,
        domain = proof.domain,
        challenge = proof.challenge,
        jws = proof.jws,
        nonce = proof.nonce
      )
  }
  given JsonDecoder[EcdsaSecp256k1Signature2019Proof] = JsonDecoder[Json_EcdsaSecp256k1Signature2019Proof].map {
    jsonProof =>
      EcdsaSecp256k1Signature2019Proof(
        jws = jsonProof.jws,
        verificationMethod = jsonProof.verificationMethod,
        created = jsonProof.created,
        challenge = jsonProof.challenge,
        domain = jsonProof.domain,
        nonce = jsonProof.nonce
      )
  }

}

object DataIntegrityProofCodecs {
  private case class Json_DataIntegrityProof(
      id: Option[String] = None,
      `type`: String,
      proofPurpose: String,
      verificationMethod: String,
      created: Option[OffsetDateTime] = None,
      domain: Option[String] = None,
      challenge: Option[String] = None,
      proofValue: String,
      cryptoSuite: String,
      previousProof: Option[String] = None,
      nonce: Option[String] = None
  )
  private given JsonEncoder[Json_DataIntegrityProof] = DeriveJsonEncoder.gen
  def proofEncoder[T <: DataIntegrityProof](cryptoSuiteValue: String): JsonEncoder[T] =
    JsonEncoder[Json_DataIntegrityProof].contramap { proof =>
      Json_DataIntegrityProof(
        proof.id,
        proof.`type`,
        proof.proofPurpose,
        proof.verificationMethod,
        proof.created.map(_.atOffset(ZoneOffset.UTC)),
        proof.domain,
        proof.challenge,
        proof.proofValue,
        cryptoSuiteValue,
        proof.previousProof,
        proof.nonce
      )
    }

  def proofDecoder[T <: DataIntegrityProof](
      createProof: (String, String, Option[Instant]) => T,
      cryptoSuiteValue: String
  ): JsonDecoder[T] = JsonDecoder[Json].mapOrFail { json =>
    for {
      proofValue <- json.get(JsonCursor.field("proofValue").isString).map(_.value)
      verificationMethod <- json.get(JsonCursor.field("verificationMethod").isString).map(_.value)
      maybeCreated <- json.get(JsonCursor.field("created")).map(_.as[Instant])
    } yield createProof(proofValue, verificationMethod, maybeCreated.toOption)
  }
}
