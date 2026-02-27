package org.hyperledger.identus.pollux.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class MultiKey(
    publicKeyMultibase: Option[MultiBaseString] = None,
    secretKeyMultibase: Option[MultiBaseString] = None
) {
  val `type`: String = "Multikey"
  val `@context`: Set[String] = Set("https://w3id.org/security/multikey/v1")
}
object MultiKey {
  private case class Json_MultiKey(
      `@context`: Set[String],
      `type`: String,
      publicKeyMultibase: Option[MultiBaseString],
      secretKeyMultibase: Option[MultiBaseString],
  )
  private given JsonEncoder[Json_MultiKey] = DeriveJsonEncoder.gen
  given JsonEncoder[MultiKey] = JsonEncoder[Json_MultiKey].contramap({ key =>
    Json_MultiKey(
      key.`@context`,
      key.`type`,
      key.publicKeyMultibase,
      key.secretKeyMultibase
    )
  })
  given JsonDecoder[MultiKey] = DeriveJsonDecoder.gen
}
