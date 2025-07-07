package org.hyperledger.identus

import org.hyperledger.identus.api.http.RequestContext
import sttp.model.Header
import zio.*
import zio.metrics.*

object LogUtils {
  inline val headerName = "X-Request-ID"

  extension [R, E, A](job: ZIO[R, E, A])
    inline def logTrace(ctx: RequestContext)(implicit trace: Trace): ZIO[R, E, A] = {
      val boundaries = MetricKeyType.Histogram.Boundaries.linear(0.0, 100.0, 10)

      def extraLog = zio.internal.stacktracer.Tracer.instance.unapply(trace) match
        case Some((location: String, file: String, line: Int)) =>
          val methodName: String = location.split('.').toSeq.takeRight(2).mkString(".")
          ZIO.logDebug(s"Trace $methodName")
        case _ => ZIO.unit // In principle this will not happen
      ctx.request.headers.find(_.name.equalsIgnoreCase(headerName)) match
        case None => (
          extraLog &>
            job.timed
              .tap {
                case (duration, res) => {
                  val seconds = duration.toMillis.toDouble / 1000
                  ZIO.logDebug(s"execution time took $seconds seconds ")
                }
              }
              .map(_._2)
        )
        case Some(header) => (
          extraLog &>
            job.timed
              .tap {
                case (duration, res) => {
                  val seconds = duration.toMillis.toDouble / 1000
                  ZIO.logDebug(s"execution time took $seconds seconds ")
                }
              }
              .map(_._2) @@ ZIOAspect.annotated("traceId", header.value)
            @@ Metric.histogram("operation_duration", boundaries).trackDurationWith(_.toMillis.toDouble)
        )

    }
}
