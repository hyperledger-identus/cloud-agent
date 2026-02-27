package org.hyperledger.identus.agent.server.http

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.json.Json
import org.hyperledger.identus.system.controller.SystemEndpoints
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*
import zio.interop.catz.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

class ZHttp4sBlazeServer(micrometerRegistry: PrometheusMeterRegistry, metricsNamespace: String) {

  private def browserFingerprint(sr: ServerRequest): Option[Sha256Hash] = {
    case class FingerPrintData(
        userAgent: Option[String],
        accept: Option[String],
        acceptLanguage: Option[String],
        acceptEncoding: Option[String],
        referrer: Option[String],
        dnt: Option[String],
        secChUa: Option[String],
        secChUaMobile: Option[String],
        secChUaPlatform: Option[String],
    )
    object FingerPrintData {
      given encoder: JsonEncoder[FingerPrintData] = DeriveJsonEncoder.gen
      given decoder: JsonDecoder[FingerPrintData] = DeriveJsonDecoder.gen
    }

    val headers = sr.headers
    val fingerPrintData = FingerPrintData(
      headers.find(_.name.toLowerCase == "user-agent").map(_.value),
      headers.find(_.name.toLowerCase == "accept").map(_.value),
      headers.find(_.name.toLowerCase == "accept-language").map(_.value),
      headers.find(_.name.toLowerCase == "accept-encoding").map(_.value),
      headers.find(_.name.toLowerCase == "referer").map(_.value),
      headers.find(_.name.toLowerCase == "dnt").map(_.value),
      headers.find(_.name.toLowerCase == "sec-ch-ua").map(_.value),
      headers.find(_.name.toLowerCase == "sec-ch-ua-mobile").map(_.value),
      headers.find(_.name.toLowerCase == "sec-ch-ua-platform").map(_.value),
    )

    val jsonStr = fingerPrintData.toJson
    val canonicalized = Json.canonicalizeToJcs(jsonStr).toOption

    canonicalized.map(x => Sha256Hash.compute(x.getBytes))
  }

  private val metricsLabel: MetricLabels = MetricLabels.Default.copy(
    forRequest = MetricLabels.Default.forRequest ++ List(
      "browser_fingerprint" -> { case (_, sr) =>
        browserFingerprint(sr) match {
          case Some(hash) => hash.hexEncoded
          case None       => "unknown"
        }
      },
      "api_key_hash" -> { case (_, sr) =>
        sr.header("apikey").map(x => Sha256Hash.compute(x.getBytes).hexEncoded).getOrElse("unknown")
      },
      "token_hash" -> { case (_, sr) =>
        sr.header("authorization")
          .map(_.split(" ").last)
          .map(x => Sha256Hash.compute(x.getBytes).hexEncoded)
          .getOrElse("unknown")
      },
    ),
  )

  private val tapirPrometheusMetricsZIO: Task[PrometheusMetrics[Task]] = ZIO.attempt {
    PrometheusMetrics.default[Task](
      namespace = metricsNamespace,
      registry = micrometerRegistry.getPrometheusRegistry,
      labels = metricsLabel
    )
  }

  private val serverOptionsZIO: ZIO[PrometheusMetrics[Task], Throwable, Http4sServerOptions[Task]] = for {
    srv <- ZIO.service[PrometheusMetrics[Task]]
    options <- ZIO.attempt {
      Http4sServerOptions
        .customiseInterceptors[Task]
        .exceptionHandler(CustomServerInterceptors.tapirExceptionHandler)
        .rejectHandler(CustomServerInterceptors.tapirRejectHandler)
        .decodeFailureHandler(CustomServerInterceptors.tapirDecodeFailureHandler)
        .serverLog(None)
        .metricsInterceptor(
          srv.metricsInterceptor(
            ignoreEndpoints = Seq(SystemEndpoints.metrics)
          )
        )
        .options
    }
  } yield options

  def start(
      endpoints: List[ZServerEndpoint[Any, Any]],
      port: Int
  ): Task[ExitCode] = {

    val serve = for {
      metrics <- tapirPrometheusMetricsZIO
      options <- serverOptionsZIO.provide(ZLayer.succeed(metrics))
      serve <- ZIO.attempt {
        val http4sEndpoints: HttpRoutes[Task] =
          ZHttp4sServerInterpreter(options)
            .from(endpoints)
            .toRoutes

        ZIO.executor.flatMap(executor =>
          BlazeServerBuilder[Task]
            .withExecutionContext(executor.asExecutionContext)
            .withServiceErrorHandler(CustomServerInterceptors.http4sServiceErrorHandler)
            .bindHttp(port, "0.0.0.0")
            .withHttpApp(Router("/" -> http4sEndpoints).orNotFound)
            .serve
            .compile
            .drain
        )
      }
    } yield serve

    serve.flatten.exitCode
  }
}

object ZHttp4sBlazeServer {
  def make(metricsNamespace: String): URIO[PrometheusMeterRegistry, ZHttp4sBlazeServer] = {
    for {
      micrometerRegistry <- ZIO.service[PrometheusMeterRegistry]
      zHttp4sBlazeServer = ZHttp4sBlazeServer(micrometerRegistry, metricsNamespace)
    } yield zHttp4sBlazeServer
  }
}
