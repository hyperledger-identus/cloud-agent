package org.hyperledger.identus.api.http

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.EndpointOutput.OneOfVariant

object EndpointOutputs {
  def statusCodeMatcher(
      statusCode: StatusCode
  ): PartialFunction[Any, Boolean] = {
    case ErrorResponse(status, _, _, _, _) if status == statusCode.code => true
  }

  def basicFailuresWith(extraFailures: OneOfVariant[ErrorResponse]*) = {
    oneOf(
      FailureVariant.badRequest,
      (FailureVariant.internalServerError +: FailureVariant.unprocessableEntity +: extraFailures)*
    )
  }

  def basicFailuresAndForbiddenWith(extraFailures: OneOfVariant[ErrorResponse]*) =
    basicFailuresWith((Seq(FailureVariant.unauthorized, FailureVariant.forbidden) ++ extraFailures)*)

  val basicFailures: EndpointOutput[ErrorResponse] = basicFailuresWith()

  val basicFailuresAndForbidden = basicFailuresWith(FailureVariant.unauthorized, FailureVariant.forbidden)

  val basicFailuresAndNotFound = basicFailuresWith(FailureVariant.notFound)

  val basicFailureAndNotFoundAndForbidden =
    basicFailuresWith(FailureVariant.notFound, FailureVariant.unauthorized, FailureVariant.forbidden)

  object FailureVariant {
    val badRequest = oneOfVariantValueMatcher(
      StatusCode.BadRequest,
      jsonBody[ErrorResponse].description("Invalid request parameters")
    )(statusCodeMatcher(StatusCode.BadRequest))

    val internalServerError = oneOfVariantValueMatcher(
      StatusCode.InternalServerError,
      jsonBody[ErrorResponse].description("Internal server error")
    )(statusCodeMatcher(StatusCode.InternalServerError))

    val notFound = oneOfVariantValueMatcher(
      StatusCode.NotFound,
      jsonBody[ErrorResponse].description("Resource could not be found")
    )(statusCodeMatcher(StatusCode.NotFound))

    val unprocessableEntity = oneOfVariantValueMatcher(
      StatusCode.UnprocessableEntity,
      jsonBody[ErrorResponse].description("Unable to process the request")
    )(statusCodeMatcher(StatusCode.UnprocessableEntity))

    val conflict = oneOfVariantValueMatcher(
      StatusCode.Conflict,
      jsonBody[ErrorResponse].description("Cannot process due to conflict with current state of the resource")
    )(statusCodeMatcher(StatusCode.Conflict))

    val forbidden = oneOfVariantValueMatcher(
      StatusCode.Forbidden,
      jsonBody[ErrorResponse].description("Forbidden")
    )(statusCodeMatcher(StatusCode.Forbidden))

    val unauthorized = oneOfVariantValueMatcher(
      StatusCode.Unauthorized,
      jsonBody[ErrorResponse].description("Unauthorized")
    )(statusCodeMatcher(StatusCode.Unauthorized))
  }

}
