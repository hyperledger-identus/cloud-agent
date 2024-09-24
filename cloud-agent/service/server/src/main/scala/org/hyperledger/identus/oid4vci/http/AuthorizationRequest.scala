package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.pollux.prex.http.PresentationExchangeTapirSchemas.given
import org.hyperledger.identus.pollux.prex.PresentationDefinition
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

//https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-5
case class AuthorizationRequest(
    presentationDefinition: Option[PresentationDefinition],
    presentationDefinitionUri: Option[String],
    clientIdScheme: Option[ClientIdScheme],
    clientMetadata: Option[Json]
)

object AuthorizationRequest {
  given Schema[Json] = Schema.any[Json] // TODO: find somewhere to share this

  given Schema[AuthorizationRequest] = Schema.derived
  given JsonEncoder[AuthorizationRequest] = DeriveJsonEncoder.gen
  given JsonDecoder[AuthorizationRequest] = DeriveJsonDecoder.gen
}

// https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-5.7
enum ClientIdScheme {
  case `pre-registered`
  case redirect_uri
  case entity_id
  case did
  case verifier_attestation
  case x509_san_dns
  case x509_san_uri
}

object ClientIdScheme {
  given schema: Schema[ClientIdScheme] = Schema.derivedEnumeration.defaultStringBased
  given encoder: JsonEncoder[ClientIdScheme] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[ClientIdScheme] = JsonDecoder[String].mapOrFail { s =>
    ClientIdScheme.values.find(_.toString == s).toRight(s"Unknown ClientIdScheme: $s")
  }
}
