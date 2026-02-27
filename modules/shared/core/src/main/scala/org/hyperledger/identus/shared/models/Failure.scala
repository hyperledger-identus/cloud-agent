package org.hyperledger.identus.shared.models

import zio.{URIO, ZIO}
import zio.json.*

trait Failure {
  def namespace: String
  def statusCode: StatusCode
  def userFacingMessage: String

  def toUnmanagedFailureException = UnmanagedFailureException(this)

  def asFailureInfo = FailureInfo(namespace, statusCode, userFacingMessage)
}

object Failure {
  extension [R, E](effect: ZIO[R, Failure, E]) {
    def orDieAsUnmanagedFailure: URIO[R, E] = {
      effect.orDieWith(f => UnmanagedFailureException(f))
    }
  }
}

final case class FailureInfo(namespace: String, statusCode: StatusCode, userFacingMessage: String) extends Failure
object FailureInfo {
  given decoder: JsonDecoder[FailureInfo] = DeriveJsonDecoder.gen[FailureInfo]
  given encoder: JsonEncoder[FailureInfo] = DeriveJsonEncoder.gen[FailureInfo]
}

case class UnmanagedFailureException(val failure: Failure) extends Throwable {
  override def getMessage: String = failure.toString
}

sealed case class StatusCode(val code: Int)

object StatusCode {
  val BadRequest: StatusCode = StatusCode(400)
  val Unauthorized: StatusCode = StatusCode(401)
  val Forbidden: StatusCode = StatusCode(403)
  val NotFound: StatusCode = StatusCode(404)
  val Conflict: StatusCode = StatusCode(409)
  val UnprocessableContent: StatusCode = StatusCode(422)

  val InternalServerError: StatusCode = StatusCode(500)
  val UnexpectedNotImplemented: StatusCode = StatusCode(501)
  val BadGateway: StatusCode = StatusCode(502)

  given decoder: JsonDecoder[StatusCode] = JsonDecoder.int.map(e => StatusCode(e))
  given encoder: JsonEncoder[StatusCode] = JsonEncoder.int.contramap((e: StatusCode) => e.code)
}
