package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.api.http.EndpointOutputs.statusCodeMatcher
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.iam.authentication.AuthenticationError
import sttp.model.StatusCode
import sttp.tapir.{oneOfVariantValueMatcher, Schema}
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.Schema.annotations.encodedName
import zio.json.{jsonField, DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import scala.util.Try

type ExtendedErrorResponse = ErrorResponse | CredentialErrorResponse

object ExtendedErrorResponse {
  given schema: Schema[ExtendedErrorResponse] =
    Schema
      .schemaForEither[ErrorResponse, CredentialErrorResponse]
      .name(Schema.SName("ExtendedErrorResponse"))
      .as
  given encoder: JsonEncoder[ExtendedErrorResponse] =
    ErrorResponse.encoder
      .orElseEither(CredentialErrorResponse.encoder)
      .contramap {
        case response: ErrorResponse           => Left(response)
        case response: CredentialErrorResponse => Right(response)
      }
  given decoder: JsonDecoder[ExtendedErrorResponse] =
    ErrorResponse.decoder
      .orElseEither(CredentialErrorResponse.decoder)
      .map {
        case Left(response)  => response
        case Right(response) => response
      }
}

case class CredentialErrorResponse(
    error: CredentialErrorCode,
    @jsonField("error_description")
    @encodedName("error_description")
    errorDescription: Option[String] = None,
    @jsonField("c_nonce")
    @encodedName("c_nonce")
    nonce: Option[String] = None,
    @jsonField("c_nonce_expires_in")
    @encodedName("c_nonce_expires_in")
    nonceExpiresIn: Option[Long] = None
)

object CredentialErrorResponse {
  given schema: Schema[CredentialErrorResponse] = Schema.derived
  given encoder: JsonEncoder[CredentialErrorResponse] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialErrorResponse] = DeriveJsonDecoder.gen

  given Conversion[AuthenticationError, CredentialErrorResponse] = ae => {
    import AuthenticationError.*
    val error = ae match {
      case _: InvalidCredentials => CredentialErrorCode.invalid_token
      case _                     => CredentialErrorCode.invalid_request
    }
    CredentialErrorResponse(error, Some(ae.userFacingMessage))
  }
}

enum CredentialErrorCode {
  case invalid_request
  case invalid_token
  case insufficient_scope
  case invalid_credential_request
  case unsupported_credential_type
  case unsupported_credential_format
  case invalid_proof
  case invalid_encryption_parameters
}

object CredentialErrorCode {
  given schema: Schema[CredentialErrorCode] = Schema.derivedEnumeration.defaultStringBased
  given encoder: JsonEncoder[CredentialErrorCode] = JsonEncoder.string.contramap(_.toString())
  given decoder: JsonDecoder[CredentialErrorCode] =
    JsonDecoder.string.mapOrFail(s =>
      Try(CredentialErrorCode.valueOf(s)).toOption.toRight(s"Unknown CredentialErrorCode: $s")
    )

  implicit class CredentialErrorCodeOps(val credentialErrorCode: CredentialErrorCode) extends AnyVal {
    def toHttpStatusCode: StatusCode = CredentialErrorCode.toHttpStatusCode(credentialErrorCode)
  }
  val toHttpStatusCode: PartialFunction[CredentialErrorCode, StatusCode] = {
    case CredentialErrorCode.invalid_request               => StatusCode.BadRequest
    case CredentialErrorCode.invalid_token                 => StatusCode.Unauthorized
    case CredentialErrorCode.insufficient_scope            => StatusCode.Forbidden
    case CredentialErrorCode.invalid_credential_request    => StatusCode.BadRequest
    case CredentialErrorCode.unsupported_credential_type   => StatusCode.BadRequest
    case CredentialErrorCode.invalid_proof                 => StatusCode.BadRequest
    case CredentialErrorCode.invalid_encryption_parameters => StatusCode.BadRequest
  }
}

object CredentialRequestErrors {
  def errorCodeMatcher(
      credentialErrorCode: CredentialErrorCode
  ): PartialFunction[Any, Boolean] = {
    case CredentialErrorResponse(code, _, _, _) if code == credentialErrorCode => true
  }

  val badRequest = oneOfVariantValueMatcher(
    StatusCode.BadRequest,
    jsonBody[ErrorResponse].description(
      "The request is missing a required parameter, includes an unsupported parameter or parameter value, repeats the same parameter, or is otherwise malformed"
    )
  )(statusCodeMatcher(StatusCode.BadRequest))

}
