package org.hyperledger.identus.server.http

import org.http4s.{MediaType, Request, Response, Status}
import org.http4s.headers.`Content-Type`
import org.http4s.server.ServiceErrorHandler
import org.hyperledger.identus.shared.models.StatusCode
import zio.{Task, ZIO}

object Http4sErrorHandler {

  def http4sServiceErrorHandler: ServiceErrorHandler[Task] = (req: Request[Task]) => { case t: Throwable =>
    val res = CustomServerInterceptors.tapirDefectHandler(
      org.hyperledger.identus.api.http.ErrorResponse(
        StatusCode.InternalServerError.code,
        s"error:InternalServerError",
        "Internal Server Error",
        Some(
          s"An unexpected error occurred when servicing the request: " +
            s"path=['${req.method.name} ${req.uri.copy(scheme = None, authority = None, fragment = None).toString}']"
        )
      ),
      Some(t)
    )
    ZIO.succeed(
      Response(Status.InternalServerError)
        .withEntity(CustomServerInterceptors.endpointOutput.codec.encode(res.value._2))
        .withContentType(`Content-Type`(MediaType.application.json))
    )
  }
}
