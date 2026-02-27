package org.hyperledger.identus.oid4vci.http

import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

case class CredentialOffer(
    credential_issuer: String,
    credential_configuration_ids: Seq[String],
    grants: Option[CredentialOfferGrant],
) {
  def offerUri: String = {
    val offerJson = CredentialOffer.encoder.encodeJson(this).toString()
    val encodedOffer = URLEncoder.encode(offerJson, StandardCharsets.UTF_8)
    s"openid-credential-offer://?credential_offer=$encodedOffer"
  }
}

object CredentialOffer {
  given schema: Schema[CredentialOffer] = Schema.derived
  given encoder: JsonEncoder[CredentialOffer] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialOffer] = DeriveJsonDecoder.gen
}

case class CredentialOfferGrant(
    authorization_code: CredentialOfferAuthorizationGrant
)

object CredentialOfferGrant {
  given schema: Schema[CredentialOfferGrant] = Schema.derived
  given encoder: JsonEncoder[CredentialOfferGrant] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialOfferGrant] = DeriveJsonDecoder.gen
}

case class CredentialOfferAuthorizationGrant(issuer_state: Option[String])

object CredentialOfferAuthorizationGrant {
  given schema: Schema[CredentialOfferAuthorizationGrant] = Schema.derived
  given encoder: JsonEncoder[CredentialOfferAuthorizationGrant] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialOfferAuthorizationGrant] = DeriveJsonDecoder.gen
}
