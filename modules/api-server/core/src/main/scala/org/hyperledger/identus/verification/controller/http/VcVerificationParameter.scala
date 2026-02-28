package org.hyperledger.identus.verification.controller.http

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.OffsetDateTime

/** Base trait for verification parameters.
  *
  * @param parameterType
  *   The type of the parameter.
  */
sealed trait VcVerificationParameter(val parameterType: String)

object VcVerificationParameter {
  given encoder: JsonEncoder[VcVerificationParameter] = DidParameter.encoder
    .orElseEither(DateTimeParameter.encoder)
    .contramap[VcVerificationParameter] {
      case did: DidParameter           => Left(did)
      case dateTime: DateTimeParameter => Right(dateTime)
    }

  given decoder: JsonDecoder[VcVerificationParameter] = DidParameter.decoder
    .orElseEither(DateTimeParameter.decoder)
    .map[VcVerificationParameter] {
      case Left(did)       => did
      case Right(dateTime) => dateTime
    }

  given schema: Schema[VcVerificationParameter] =
    Schema
      .oneOfUsingField[VcVerificationParameter, String](a => a.parameterType, t => t)(
        ("DidParameter", DidParameter.schema),
        ("DateTimeParameter", DateTimeParameter.schema)
      )

}

/** Parameter for DID-based verifications.
  *
  * @param did
  *   The DID (Decentralized Identifier) to use for verification.
  */
case class DidParameter(
    @description("The DID (Decentralized Identifier) to use for verification.")
    @encodedExample("did:prism:issuer")
    did: String
) extends VcVerificationParameter("DidParameter")

object DidParameter {
  given encoder: JsonEncoder[DidParameter] =
    DeriveJsonEncoder.gen[DidParameter]

  given decoder: JsonDecoder[DidParameter] =
    DeriveJsonDecoder.gen[DidParameter]

  given schema: Schema[DidParameter] = Schema.derived
}

/** Parameter for date-time based verifications.
  *
  * @param dateTime
  *   The date and time to use for verification.
  */
case class DateTimeParameter(
    @description("The date and time to use for verification.")
    @encodedExample("2022-03-10T12:00:00Z")
    dateTime: OffsetDateTime
) extends VcVerificationParameter("DateTimeParameter")

object DateTimeParameter {
  given encoder: JsonEncoder[DateTimeParameter] =
    DeriveJsonEncoder.gen[DateTimeParameter]

  given decoder: JsonDecoder[DateTimeParameter] =
    DeriveJsonDecoder.gen[DateTimeParameter]

  given schema: Schema[DateTimeParameter] = Schema.derived
}
