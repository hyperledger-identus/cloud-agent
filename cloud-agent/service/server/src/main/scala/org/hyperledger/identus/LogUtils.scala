package org.hyperledger.identus

import org.hyperledger.identus.api.http.RequestContext
import sttp.model.Header
import zio.*

object LogUtils {
  inline val headerName = "X-Request-ID"

  extension [R, E, A](job: ZIO[R, E, A])
    inline def logTrace(ctx: RequestContext)(implicit trace: Trace): ZIO[R, E, A] = {
      // TODO We can add a timer here
      // TODO We can also send metric (couter and/or timer) to another system from here
      def extraLog = zio.internal.stacktracer.Tracer.instance.unapply(trace) match
        case Some((location: String, file: String, line: Int)) =>
          val methodName: String = location.split('.').toSeq.takeRight(2).mkString(".")
          ZIO.logDebug(s"Trace $methodName")
        case _ => ZIO.unit // In principle this will not happen
      ctx.request.headers.find(_.name.equalsIgnoreCase(headerName)) match
        case None         => (extraLog &> job)
        case Some(header) => (extraLog &> job) @@ ZIOAspect.annotated("traceId", header.value)
    }
}
