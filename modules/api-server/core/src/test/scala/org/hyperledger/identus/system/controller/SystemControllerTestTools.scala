package org.hyperledger.identus.system.controller

import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import org.hyperledger.identus.agent.server.http.CustomServerInterceptors
import org.hyperledger.identus.agent.server.SystemModule.configLayer
import org.hyperledger.identus.system.controller.http.HealthInfo
import sttp.client3.{DeserializationException, Response, UriContext}
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.RIOMonadError
import zio.*
import zio.metrics.connectors.micrometer
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.metrics.jvm.DefaultJvmMetrics
import zio.test.ZIOSpecDefault

trait SystemControllerTestTools {
  self: ZIOSpecDefault =>

  type HealthInfoResponse =
    Response[Either[DeserializationException[String], HealthInfo]]
  type MetricsResponse =
    Response[
      Either[String, String]
    ]

  private val controllerLayer = ZLayer.succeed(PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) >+>
    configLayer >+>
    ZLayer.succeed(MicrometerConfig.default) >+>
    DefaultJvmMetrics.live.unit >+>
    micrometer.micrometerLayer >+>
    SystemControllerImpl.layer

  val testEnvironmentLayer = zio.test.testEnvironment ++
    controllerLayer

  val systemUriBase = uri"http://test.com/_system/"

  def bootstrapOptions[F[_]](monadError: MonadError[F]): CustomiseInterceptors[F, Any] = {
    new CustomiseInterceptors[F, Any](_ => ())
      .exceptionHandler(CustomServerInterceptors.tapirExceptionHandler)
      .rejectHandler(CustomServerInterceptors.tapirRejectHandler)
      .decodeFailureHandler(CustomServerInterceptors.tapirDecodeFailureHandler)
  }

  def httpBackend(controller: SystemController) = {
    val systemEndpoints = SystemServerEndpoints(controller)

    val backend =
      TapirStubInterpreter(
        bootstrapOptions(new RIOMonadError[Any]),
        SttpBackendStub(new RIOMonadError[Any])
      )
        .whenServerEndpoint(systemEndpoints.healthEndpoint)
        .thenRunLogic()
        .whenServerEndpoint(systemEndpoints.metricsEndpoint)
        .thenRunLogic()
        .backend()
    backend
  }

}
