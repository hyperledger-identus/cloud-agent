package org.hyperledger.identus.credentials.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}

import java.time.{Instant, OffsetDateTime, ZoneOffset}

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
