package org.hyperledger.identus.pollux.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.{Instant, OffsetDateTime, ZoneOffset}

case class EcdsaSecp256k1VerificationKey2019(
    publicKeyJwk: JsonWebKey,
    id: Option[String] = None,
    controller: Option[String] = None,
    expires: Option[Instant] = None
) {
  val `type`: String = "EcdsaSecp256k1VerificationKey2019"
  val `@context`: Set[String] = Set("https://w3id.org/security/v1")
}

object EcdsaSecp256k1VerificationKey2019 {
  private case class Json_EcdsaSecp256k1VerificationKey2019(
      `@context`: Set[String],
      `type`: String,
      id: Option[String],
      controller: Option[String],
      publicKeyJwk: JsonWebKey,
      expires: Option[OffsetDateTime]
  )
  private given JsonEncoder[Json_EcdsaSecp256k1VerificationKey2019] = DeriveJsonEncoder.gen
  given JsonEncoder[EcdsaSecp256k1VerificationKey2019] = JsonEncoder[Json_EcdsaSecp256k1VerificationKey2019].contramap {
    key =>
      Json_EcdsaSecp256k1VerificationKey2019(
        key.`@context`,
        key.`type`,
        key.id,
        key.controller,
        key.publicKeyJwk,
        key.expires.map(_.atOffset(ZoneOffset.UTC))
      )
  }
  given JsonDecoder[EcdsaSecp256k1VerificationKey2019] = DeriveJsonDecoder.gen

}
