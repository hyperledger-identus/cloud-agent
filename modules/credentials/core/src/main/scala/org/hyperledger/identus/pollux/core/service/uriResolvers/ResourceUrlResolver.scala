package org.hyperledger.identus.pollux.core.service.uriResolvers

import org.hyperledger.identus.shared.http.{GenericUriResolverError, InvalidUri, UriResolver}
import org.hyperledger.identus.shared.models.StatusCode
import zio.*

import java.net.URI
import scala.util.Try

class ResourceUrlResolver(extraResources: Map[String, String]) extends UriResolver {
  import ResourceUrlResolver.*

  def resolve(uri: String): IO[GenericUriResolverError, String] = {
    for {
      javaUri <- ZIO.fromTry(Try(URI(uri))).mapError(_ => InvalidUri(uri))
      scheme <- ZIO.succeed(javaUri.getScheme)
      body <- scheme match
        case "resource" =>
          val inputStream = this.getClass.getResourceAsStream(javaUri.getPath)
          if (inputStream != null)
            val content = scala.io.Source.fromInputStream(inputStream).mkString
            inputStream.close()
            ZIO.succeed(content)
          else ZIO.fail(ResourceNotFound(uri))
        case _ =>
          extraResources
            .get(uri)
            .map(ZIO.succeed(_))
            .getOrElse(ZIO.fail(ResourceNotFound(uri)))
    } yield body

  }

}

class ResourceUrlResolverError(statusCode: StatusCode, userFacingMessage: String)
    extends GenericUriResolverError(statusCode, userFacingMessage)

object ResourceUrlResolver {
  def layer: ULayer[ResourceUrlResolver] =
    ZLayer.succeed(new ResourceUrlResolver(Map.empty))

  def layerWithExtraResources: URLayer[Map[String, String], ResourceUrlResolver] =
    ZLayer.fromFunction(ResourceUrlResolver(_))

  final case class InvalidURI(uri: String)
      extends ResourceUrlResolverError(
        StatusCode.UnprocessableContent,
        s"The URI to resolve is invalid: uri=[$uri]"
      )

  final case class ResourceNotFound(uri: String)
      extends ResourceUrlResolverError(
        StatusCode.NotFound,
        s"The resource was not found on the URI's underlying server: uri=[$uri]"
      )
}
