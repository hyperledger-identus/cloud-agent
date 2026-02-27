package org.hyperledger.identus.mercury.protocol.invitation

import org.hyperledger.identus.mercury.protocol.invitation.v2.*
import zio.json.DecoderOps

import java.net.{URI, URL}
import java.util as ju

object OutOfBand {

  def parseLink(url: String): Option[String] = parseLink(new URI(url).toURL())
  def parseLink(url: URL): Option[String] = (url.getQuery() match {
    case str if str.startsWith("_oob=") => Some(str.drop(5))
    case _                              => None
  }).map { e =>
    val decoder = ju.Base64.getUrlDecoder()
    String(decoder.decode(e))
  }

  def parseInvitation(url: String): Either[String | RuntimeException, Invitation] =
    parseLink(url) match {
      case Some(e) => e.fromJson[Invitation]
      case None    => Left(new RuntimeException("Expecting a url!"))
    }

}
