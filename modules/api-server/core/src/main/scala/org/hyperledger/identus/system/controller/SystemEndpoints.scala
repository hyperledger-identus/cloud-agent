package org.hyperledger.identus.system.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.system.controller.http.HealthInfo
import sttp.apispec.Tag
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.stringBody

object SystemEndpoints {

  private val tagName = "System"
  private val tagDescription =
    s"""
       |The __${tagName}__ is a REST API that allows to check the system health and scrap the runtime metrics.
       |
       |The __health__ endpoint returns the current version of the running service.
       |This information can be used to check the health status of the running service in the docker or kubernetes environment.
       |
       |The __metrics__ endpoint returns the runtime metrics of the running service scraped from the internal prometheus registry.
       |This information is collected by the prometheus server and can be used to monitor the running service.
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  val health: PublicEndpoint[
    (RequestContext),
    ErrorResponse,
    HealthInfo,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("_system" / "health")
      .out(jsonBody[HealthInfo].description("The health info object."))
      .errorOut(basicFailures)
      .tag(tagName)
      .summary("Check the health status of the running service")
      .description("Returns the health info object of the running service")
      .name("systemHealth")

  val metrics: PublicEndpoint[
    (RequestContext),
    ErrorResponse,
    String,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("_system" / "metrics")
      .out(stringBody.description("The metrics as plain strings."))
      .errorOut(basicFailures)
      .tag(tagName)
      .summary("Collect the runtime metrics of the running service")
      .description("Returns the metrics of the running service from the internal Prometheus registry")
      .name("systemMetrics")

}
