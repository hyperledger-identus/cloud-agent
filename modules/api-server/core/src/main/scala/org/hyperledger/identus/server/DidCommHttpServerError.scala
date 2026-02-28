package org.hyperledger.identus.server

sealed trait DidCommHttpServerError

object DidCommHttpServerError {
  case class InvalidContentTypeError(error: String) extends DidCommHttpServerError
  case class RequestBodyParsingError(error: String) extends DidCommHttpServerError
  case class DIDCommMessageParsingError(error: String) extends DidCommHttpServerError
}
