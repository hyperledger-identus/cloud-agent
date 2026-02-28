package org.hyperledger.identus.shared.http

import io.lemonlabs.uri.{Uri, Url, Urn}
import org.hyperledger.identus.shared.models.{Failure, PrismEnvelopeData, StatusCode}
import org.hyperledger.identus.shared.utils.Base64Utils
import zio.*
import zio.json.*

import scala.util
import scala.util.Try

trait UriResolver {

  def resolve(uri: String): IO[GenericUriResolverError, String]

}

class GenericUriResolver(resolvers: Map[String, UriResolver]) extends UriResolver {

  override def resolve(uri: String): IO[GenericUriResolverError, String] = {
    val parsedUri = Uri.parseTry(uri)

    ZIO.logDebug(s"Resolving resource from uri: $uri") *>
      ZIO.fromTry(parsedUri).mapError(_ => InvalidUri(uri)).flatMap {
        case url: Url =>
          url.schemeOption.fold(ZIO.fail(InvalidUri(uri)))(schema =>
            resolvers.get(schema).fold(ZIO.fail(UnsupportedUriSchema(schema))) { resolver =>
              resolver.resolve(uri).flatMap { res =>
                schema match
                  case "did" =>
                    res.fromJson[PrismEnvelopeData] match
                      case Right(env) =>
                        ZIO
                          .fromTry(Try(Base64Utils.decodeUrlToString(env.resource)))
                          .mapError(_ => DidUriResponseNotEnvelope(uri))
                      case Left(err) =>
                        ZIO.logDebug(s"Failed to parse response as PrismEnvelope: $err") *>
                          ZIO.logDebug("Falling back to returning the response as is") *>
                          ZIO.succeed(res)
                  case _ => ZIO.succeed(res)
              }
            }
          )

        case Urn(path) => ZIO.fail(InvalidUri(uri)) // Must be a URL
      }

  }

}

trait GenericUriResolverError(val statusCode: StatusCode, val userFacingMessage: String) extends Failure {
  override val namespace: String = "UriResolver"
  def toThrowable: Throwable = {
    this match
      case InvalidUri(uri)              => new RuntimeException(s"Invalid URI: $uri")
      case UnsupportedUriSchema(schema) => new RuntimeException(s"Unsupported URI schema: $schema")
  }
}

case class DidUriResponseNotEnvelope(uri: String)
    extends GenericUriResolverError(
      StatusCode.UnprocessableContent,
      s"The response of DID uri resolution was not prism envelope: uri=[$uri]"
    )

case class InvalidUri(uri: String)
    extends GenericUriResolverError(StatusCode.UnprocessableContent, s"The URI to dereference is invalid: uri=[$uri]")

case class UnsupportedUriSchema(schema: String)
    extends GenericUriResolverError(StatusCode.UnprocessableContent, s"Unsupported URI schema: $schema")
