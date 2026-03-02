package org.hyperledger.identus.system.controller

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.system.controller.http.HealthInfo
import zio.*

class SystemControllerImpl(
    prometheusRegistry: PrometheusMeterRegistry,
    version: String
) extends SystemController {

  override def health()(implicit rc: RequestContext): IO[ErrorResponse, HealthInfo] = {
    ZIO.succeed(HealthInfo(version = version))
  }

  override def metrics()(implicit rc: RequestContext): IO[ErrorResponse, String] = {
    ZIO.succeed(prometheusRegistry.scrape)
  }

}

object SystemControllerImpl {
  def layer(version: String): URLayer[PrometheusMeterRegistry, SystemController] =
    ZLayer.fromFunction((registry: PrometheusMeterRegistry) => SystemControllerImpl(registry, version))
}
