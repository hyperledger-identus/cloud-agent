package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.pollux.core.model.oid4vci.CredentialIssuer as PolluxCredentialIssuer
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.util.UUID

case class CreateCredentialIssuerRequest(
    id: Option[UUID],
    authorizationServer: AuthorizationServer,
)

object CreateCredentialIssuerRequest {
  given schema: Schema[CreateCredentialIssuerRequest] = Schema.derived
  given encoder: JsonEncoder[CreateCredentialIssuerRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CreateCredentialIssuerRequest] = DeriveJsonDecoder.gen
}

case class AuthorizationServer(url: String, clientId: String, clientSecret: String)

object AuthorizationServer {
  given schema: Schema[AuthorizationServer] = Schema.derived
  given encoder: JsonEncoder[AuthorizationServer] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[AuthorizationServer] = DeriveJsonDecoder.gen
}

case class CredentialIssuer(id: UUID, authorizationServerUrl: String)

case class PatchAuthorizationServer(url: Option[String], clientId: Option[String], clientSecret: Option[String])

object PatchAuthorizationServer {
  given schema: Schema[PatchAuthorizationServer] = Schema.derived
  given encoder: JsonEncoder[PatchAuthorizationServer] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[PatchAuthorizationServer] = DeriveJsonDecoder.gen
}

case class PatchCredentialIssuerRequest(authorizationServer: Option[PatchAuthorizationServer] = None)

object PatchCredentialIssuerRequest {
  given schema: Schema[PatchCredentialIssuerRequest] = Schema.derived
  given encoder: JsonEncoder[PatchCredentialIssuerRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[PatchCredentialIssuerRequest] = DeriveJsonDecoder.gen
}

object CredentialIssuer {
  given schema: Schema[CredentialIssuer] = Schema.derived
  given encoder: JsonEncoder[CredentialIssuer] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialIssuer] = DeriveJsonDecoder.gen

  given Conversion[PolluxCredentialIssuer, CredentialIssuer] = domain =>
    CredentialIssuer(domain.id, domain.authorizationServer.toString)
}

case class CredentialIssuerPage(
    self: String,
    kind: String = "CredentialIssuerPage",
    pageOf: String,
    next: Option[String] = None,
    previous: Option[String] = None,
    contents: Seq[CredentialIssuer]
)

object CredentialIssuerPage {
  given schema: Schema[CredentialIssuerPage] = Schema.derived
  given encoder: JsonEncoder[CredentialIssuerPage] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialIssuerPage] = DeriveJsonDecoder.gen
}
