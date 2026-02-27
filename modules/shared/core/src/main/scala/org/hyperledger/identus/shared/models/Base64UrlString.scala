package org.hyperledger.identus.shared.models

import org.hyperledger.identus.shared.utils.Base64Utils

import scala.util.Try

opaque type Base64UrlString = String

object Base64UrlString {
  def fromStringUnsafe(s: String): Base64UrlString = s
  def fromString(s: String): Try[Base64UrlString] = Try(Base64Utils.decodeURL(s)).map(_ => s)
  def fromByteArray(bytes: Array[Byte]): Base64UrlString = Base64Utils.encodeURL(bytes)

  extension (s: Base64UrlString) {
    def toStringNoPadding: String = s.takeWhile(_ != '=')
    def toByteArray: Array[Byte] = Base64Utils.decodeURL(s)
    def toString: String = s
  }
}
