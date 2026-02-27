package org.hyperledger.identus.pollux.core.model.primitives

import zio.prelude.Validation

import java.net.URI
import scala.util.Try

opaque type UrlString = String

object UrlString {
  def make(value: String): Validation[String, UrlString] = {
    Try(URI(value).toURL).fold(
      _ => Validation.fail(s"Invalid URL: $value"),
      _ => Validation.succeed(value)
    )
  }

  extension (urlString: UrlString) {
    def toUrl: java.net.URL = new java.net.URI(urlString).toURL
    def value: String = urlString
  }
  extension (url: java.net.URL) {
    def toUrlString: UrlString = url.toString
  }
  given CanEqual[UrlString, UrlString] = CanEqual.derived
}
