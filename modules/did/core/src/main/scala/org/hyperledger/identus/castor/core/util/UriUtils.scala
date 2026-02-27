package org.hyperledger.identus.castor.core.util

import io.lemonlabs.uri.{QueryString, Uri, Url, Urn}
import io.lemonlabs.uri.config.UriConfig
import io.lemonlabs.uri.decoding.UriDecodeException
import io.lemonlabs.uri.encoding.PercentEncoder

// TODO: unify with the logic used in Node
// https://github.com/input-output-hk/atala-prism/blob/ba7b3e3ef307f6bd06734af2bf8fed9b119ee98e/prism-backend/common/src/main/scala/io/iohk/atala/prism/utils/UriUtils.scala
object UriUtils {

  /** Normalized URI according to <a
    * href="https://www.rfc-editor.org/rfc/rfc3986#section-6">RFC&nbsp;3986,&nbsp;section-6</a>
    *
    * @param uri
    * @return
    *   [[Some]](uri) - normalized uri, if it is a valid uri string, or [[None]]
    */
  def normalizeUri(uriStr: String): Option[String] = {

    /*
     * List of normalizations performed:
     *   percent encoding normalization
     *     decode unreserved characters
     *   case normalization
     *     scheme and host to lowercase
     *     all percent encoded triplets use uppercase hexadecimal chars
     *   path segment normalization
     *     remove "." and ".." segments from path
     *     remove duplicate forward slashes (//) from path
     *   scheme specific normalization (http, https) since it is likely to be often used type of URL
     *     remove default port
     *     sort query parameters by key alphabetically
     *     remove duplicates (by name/key)
     *     encode special characters that are disallowed in path and query
     *     decode the ones that are allowed if encoded
     *
     * for URN:
     *   convert to lowercase
     *   decode all percent encoded triplets (including unreserved)
     *   encode any that need to be encodedparsingutils
     *
     * URL without a scheme is treated as invalid
     */
    implicit val config: UriConfig = UriConfig.default.copy(queryEncoder = PercentEncoder())

    try {
      // parsing decodes the percent encoded triplets, including unreserved chars
      val parsed = Uri.parse(uriStr)
      parsed match {
        case url: Url =>
          if (url.schemeOption.isEmpty) throw new UriDecodeException("Scheme is empty")
          // lowercase schema
          val schemeNormalized = url.schemeOption.map(_.toLowerCase())

          // lowercase host if not IP
          val hostNormalized = url.hostOption.map(_.normalize)

          // removes dot segments and extra //
          val pathNormalized = url.path.normalize(removeEmptyParts = true)

          // remove unneeded ports
          val portNormalized = url.port.flatMap { (port: Int) =>
            schemeNormalized match {
              case Some(scheme) =>
                scheme match {
                  case "http"       => if (port == 80) None else Some(port)
                  case "https"      => if (port == 443) None else Some(port)
                  case "ftp"        => if (port == 21) None else Some(port)
                  case "ws" | "wss" => if (port == 80) None else Some(port)
                  case _            => Some(port)
                }
              case None => Some(port)
            }
          }

          val queryNormalized = {
            // filter duplicate keys (last one stays) and sort alphabetically (by key)
            val filtered = url.query.params.toMap.toVector.sortBy(_._1)
            QueryString(filtered)
          }

          // construction of the instance encodes all special characters that are disallowed in path and query
          val urlNormalized = Url(
            scheme = schemeNormalized.orNull,
            user = url.user.orNull,
            password = url.password.orNull,
            host = hostNormalized.map(_.toString).orNull,
            port = portNormalized.getOrElse(-1),
            path = pathNormalized.toString,
            query = queryNormalized,
            fragment = url.fragment.orNull
          )

          Some(urlNormalized.toString)

        case urn: Urn =>
          Some(urn.toString.toLowerCase)
      }
    } catch {
      case _: Exception => None
    }
  }

  def isValidUriString(str: String): Boolean = {
    try {
      Uri.parse(str) match {
        case url: Url => url.schemeOption.nonEmpty
        case Urn(_)   => true
      }
    } catch {
      case _: Exception => false
    }
  }

  /** Checks if a string is a valid URI fragment according to <a
    * href="https://www.rfc-editor.org/rfc/rfc3986#section-3.5">RFC&nbsp;3986&nbsp;section-3.5</a>
    *
    * @param str
    * @return
    *   true if str is a valid URI fragment, otherwise false
    */
  def isValidUriFragment(str: String): Boolean = {

    /*
     * Alphanumeric characters (A-Z, a-z, 0-9)
     * Some special characters: -._~!$&'()*+,;=:@
     * Percent-encoded characters, which are represented by the pattern %[0-9A-Fa-f]{2}
     */
    val uriFragmentRegex = "^([A-Za-z0-9\\-._~!$&'()*+,;=:@/?]|%[0-9A-Fa-f]{2})*$".r

    // In general, empty URI fragment is a valid fragment, but for our use-case it would be pointless
    str.nonEmpty && uriFragmentRegex.matches(str)
  }
}
