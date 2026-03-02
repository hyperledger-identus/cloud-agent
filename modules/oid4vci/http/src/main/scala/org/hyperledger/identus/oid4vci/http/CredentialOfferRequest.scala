package org.hyperledger.identus.oid4vci.http

import sttp.tapir.json.zio.schemaForZioJsonValue
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class CredentialOfferRequest(
    credentialConfigurationId: String,
    issuingDID: String,
    claims: zio.json.ast.Json,
)

object CredentialOfferRequest {
  given schema: Schema[CredentialOfferRequest] = Schema.derived
  given encoder: JsonEncoder[CredentialOfferRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialOfferRequest] = DeriveJsonDecoder.gen
}

case class CredentialOfferResponse(credentialOffer: String)

object CredentialOfferResponse {
  given schema: Schema[CredentialOfferResponse] = Schema.derived
  given encoder: JsonEncoder[CredentialOfferResponse] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialOfferResponse] = DeriveJsonDecoder.gen
}
