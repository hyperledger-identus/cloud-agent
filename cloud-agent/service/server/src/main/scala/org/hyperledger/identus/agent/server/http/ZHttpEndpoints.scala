package org.hyperledger.identus.agent.server.http

import org.hyperledger.identus.agent.server.buildinfo.BuildInfo
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

  def swaggerEndpoints[F[_]](apiEndpoints: List[ServerEndpoint[Any, F]]): List[ServerEndpoint[Any, F]] =
    SwaggerInterpreter(swaggerUIOptions = swaggerUIOptions, customiseDocsModel = DocModels.customiseDocsModel)
      .fromServerEndpoints[F](apiEndpoints, "Identus Cloud Agent", BuildInfo.version)

  def redocEndpoints[F[_]](apiEndpoints: List[ServerEndpoint[Any, F]]): List[ServerEndpoint[Any, F]] =
    RedocInterpreter(redocUIOptions = redocUIOptions, customiseDocsModel = DocModels.customiseDocsModel)
      .fromServerEndpoints[F](apiEndpoints, "Identus Cloud Agent", BuildInfo.version)

  def withDocumentations[F[_]](apiEndpoints: List[ServerEndpoint[Any, F]]): List[ServerEndpoint[Any, F]] = {
    apiEndpoints ++ swaggerEndpoints[F](apiEndpoints) ++ redocEndpoints[F](apiEndpoints)
  }
}
