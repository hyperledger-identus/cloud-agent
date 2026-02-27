package org.hyperledger.identus.pollux.core.model.primitives

import zio.prelude.Validation

import java.net.URI
import scala.util.Try

opaque type UriString = String

object UriString {

  def make(value: String): Validation[String, UriString] = {
    Try(URI(value)).fold(
      _ => Validation.fail(s"Invalid URI: $value"),
      _ => Validation.succeed(value)
    )
  }

  extension (uriString: UriString) {
    def toUri: java.net.URI = new java.net.URI(uriString)
    def value: String = uriString
  }

  extension (uri: java.net.URI) {
    def toUriString: UriString = uri.toString
  }

  given CanEqual[UriString, UriString] = CanEqual.derived
}
