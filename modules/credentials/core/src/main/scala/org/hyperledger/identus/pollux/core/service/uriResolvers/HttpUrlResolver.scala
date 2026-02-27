package org.hyperledger.identus.pollux.core.service.uriResolvers

import org.hyperledger.identus.shared.http.{GenericUriResolverError, UriResolver}
import org.hyperledger.identus.shared.models.StatusCode
import zio.*
import zio.http.*

import java.nio.charset.StandardCharsets

class HttpUrlResolver(client: Client) extends UriResolver {
  import HttpUriResolver.*
  override def resolve(uri: String): IO[GenericUriResolverError, String] = {
    val program = for {
      url <- ZIO.fromEither(URL.decode(uri)).mapError(_ => InvalidURI(uri))
      response <- client
        .request(Request(url = url))
        .mapError(t => ConnectionError(t.getMessage))
      body <- response.status match {
        case Status.Ok =>
          response.body.asString.mapError(t => ResponseProcessingError(t.getMessage))
        case Status.NotFound =>
          ZIO.fail(ResourceNotFound(uri))
        case status if status.isError =>
          response.body.asStream
            .take(1024) // Only take the first 1024 bytes from the response body (if any).
            .runCollect
            .map(c => new String(c.toArray, StandardCharsets.UTF_8))
            .orDie
            .flatMap(errorMessage => ZIO.fail(UnexpectedUpstreamResponseReceived(status.code, Some(errorMessage))))
        case status =>
          ZIO.fail(UnexpectedUpstreamResponseReceived(status.code))
      }
    } yield body
    program.provideSomeLayer(zio.Scope.default)
  }

}

object HttpUriResolver {
  val layer: URLayer[Client, HttpUrlResolver] = ZLayer.fromFunction(HttpUrlResolver(_))

  class HttpUriResolverError(statusCode: StatusCode, userFacingMessage: String)
      extends GenericUriResolverError(statusCode, userFacingMessage)

  final case class InvalidURI(uri: String)
      extends HttpUriResolverError(
        StatusCode.UnprocessableContent,
        s"The URI to resolve is invalid: uri=[$uri]"
      )

  final case class ConnectionError(cause: String)
      extends HttpUriResolverError(
        StatusCode.BadGateway,
        s"An error occurred while connecting to the URI's underlying server: cause=[$cause]"
      )

  final case class ResourceNotFound(uri: String)
      extends HttpUriResolverError(
        StatusCode.NotFound,
        s"The resource was not found on the URI's underlying server: uri=[$uri]"
      )

  final case class ResponseProcessingError(cause: String)
      extends HttpUriResolverError(
        StatusCode.BadGateway,
        s"An error occurred while processing the URI's underlying server response: cause=[$cause]"
      )

  final case class UnexpectedUpstreamResponseReceived(status: Int, content: Option[String] = None)
      extends HttpUriResolverError(
        StatusCode.BadGateway,
        s"An unexpected response was received from the URI's underlying server: status=[$status], content=[${content.getOrElse("n/a")}]"
      )

}
