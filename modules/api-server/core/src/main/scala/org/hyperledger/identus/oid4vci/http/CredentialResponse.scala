package org.hyperledger.identus.oid4vci.http

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.encodedName
import zio.json.*
import zio.json.ast.Json

sealed trait CredentialResponse {
  // OPTIONAL. String containing a nonce to be used to create a proof of possession of key material when requesting a Credential (see Section 7.2).
  // When received, the Wallet MUST use this nonce value for its subsequent Credential Requests until the Credential Issuer provides a fresh nonce.
  val nonce: Option[String]
  // OPTIONAL. String identifying an issued Credential that the Wallet includes in the Notification Request as defined in Section 10.1.
  // This parameter MUST NOT be present if credential parameter is not present
  val nonceExpiresIn: Option[Int]
}

object CredentialResponse {
  given schema: Schema[CredentialResponse] = Schema.derived
  given decoder: JsonDecoder[CredentialResponse] =
    ImmediateCredentialResponse.decoder
      .orElse(DeferredCredentialResponse.decoder.widen)
  given encoder: JsonEncoder[CredentialResponse] = JsonEncoder[Json].contramap { resp =>
    val jsonAst = resp match {
      case i: ImmediateCredentialResponse => i.toJsonAST
      case i: DeferredCredentialResponse  => i.toJsonAST
    }
    jsonAst.getOrElse(throw new RuntimeException(s"Failed to encode CredentialResponse to JSON: ${resp}"))
  }
}

case class ImmediateCredentialResponse(
    credential: String,
    @jsonField("c_nonce") @encodedName("c_nonce") nonce: Option[String] = None,
    @jsonField("c_nonce_expires_in") @encodedName("c_nonce_expires_in") nonceExpiresIn: Option[Int] = None
) extends CredentialResponse

object ImmediateCredentialResponse {
  given schema: Schema[ImmediateCredentialResponse] = Schema.derived
  given encoder: JsonEncoder[ImmediateCredentialResponse] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[ImmediateCredentialResponse] = DeriveJsonDecoder.gen
}

case class DeferredCredentialResponse(
    @jsonField("transaction_id") @encodedName("transaction_id") transactionId: String,
    @jsonField("c_nonce") @encodedName("c_nonce") nonce: Option[String],
    @jsonField("c_nonce_expires_in") @encodedName("c_nonce_expires_in") nonceExpiresIn: Option[Int]
) extends CredentialResponse

object DeferredCredentialResponse {
  given schema: Schema[DeferredCredentialResponse] = Schema.derived
  given encoder: JsonEncoder[DeferredCredentialResponse] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[DeferredCredentialResponse] = DeriveJsonDecoder.gen
}
