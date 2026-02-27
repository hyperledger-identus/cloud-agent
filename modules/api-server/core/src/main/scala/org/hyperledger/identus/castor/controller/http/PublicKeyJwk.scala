package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.castor.core.model.did.w3c
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class PublicKeyJwk(
    crv: Option[String] = None,
    x: Option[String] = None,
    y: Option[String] = None,
    kty: String
)

object PublicKeyJwk {
  given encoder: JsonEncoder[PublicKeyJwk] = DeriveJsonEncoder.gen[PublicKeyJwk]
  given decoder: JsonDecoder[PublicKeyJwk] = DeriveJsonDecoder.gen[PublicKeyJwk]
  given schema: Schema[PublicKeyJwk] = Schema.derived

  given Conversion[w3c.PublicKeyJwk, PublicKeyJwk] = (publicKeyJwk: w3c.PublicKeyJwk) =>
    PublicKeyJwk(
      crv = Some(publicKeyJwk.crv),
      x = publicKeyJwk.x,
      y = publicKeyJwk.y,
      kty = publicKeyJwk.kty
    )
}
