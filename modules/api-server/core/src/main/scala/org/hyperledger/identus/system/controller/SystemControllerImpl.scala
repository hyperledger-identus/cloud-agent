package org.hyperledger.identus.system.controller

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.hyperledger.identus.agent.server.buildinfo.BuildInfo
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.system.controller.http.HealthInfo
import zio.*

class SystemControllerImpl(
    prometheusRegistry: PrometheusMeterRegistry
) extends SystemController {

  override def health()(implicit rc: RequestContext): IO[ErrorResponse, HealthInfo] = {
    ZIO.succeed(HealthInfo(version = BuildInfo.version))
  }

  override def metrics()(implicit rc: RequestContext): IO[ErrorResponse, String] = {
    ZIO.succeed(prometheusRegistry.scrape)
  }

}

object SystemControllerImpl {
  val layer: URLayer[PrometheusMeterRegistry, SystemController] =
    ZLayer.fromFunction(SystemControllerImpl(_))
}
