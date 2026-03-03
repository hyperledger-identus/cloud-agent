package org.hyperledger.identus.server.http

import sttp.apispec.openapi.OpenAPI
import sttp.tapir.redoc.bundle.RedocInterpreter
import sttp.tapir.redoc.RedocUIOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.swagger.SwaggerUIOptions

object ZHttpEndpoints {

  private val swaggerUIOptions = SwaggerUIOptions.default

  private val redocUIOptions = RedocUIOptions.default
    .copy(pathPrefix = List("redoc"))

  def swaggerEndpoints[F[_]](
      apiEndpoints: List[ServerEndpoint[Any, F]],
      appName: String,
      version: String,
      customiseDocsModel: OpenAPI => OpenAPI
  ): List[ServerEndpoint[Any, F]] =
    SwaggerInterpreter(swaggerUIOptions = swaggerUIOptions, customiseDocsModel = customiseDocsModel)
      .fromServerEndpoints[F](apiEndpoints, appName, version)

  def redocEndpoints[F[_]](
      apiEndpoints: List[ServerEndpoint[Any, F]],
      appName: String,
      version: String,
      customiseDocsModel: OpenAPI => OpenAPI
  ): List[ServerEndpoint[Any, F]] =
    RedocInterpreter(redocUIOptions = redocUIOptions, customiseDocsModel = customiseDocsModel)
      .fromServerEndpoints[F](apiEndpoints, appName, version)

  def withDocumentations[F[_]](
      apiEndpoints: List[ServerEndpoint[Any, F]],
      appName: String,
      version: String,
      customiseDocsModel: OpenAPI => OpenAPI
  ): List[ServerEndpoint[Any, F]] = {
    apiEndpoints ++
      swaggerEndpoints[F](apiEndpoints, appName, version, customiseDocsModel) ++
      redocEndpoints[F](apiEndpoints, appName, version, customiseDocsModel)
  }
}
