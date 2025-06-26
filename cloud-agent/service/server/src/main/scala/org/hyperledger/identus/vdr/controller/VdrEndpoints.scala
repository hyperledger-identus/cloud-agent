package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.EndpointOutputs
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.vdr.controller.http.CreateVdrEntryResponse
import sttp.apispec.Tag
import sttp.model.QueryParams
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

object VdrEndpoints {

  private val tagName = "VDR"
  private val tagDescription =
    s"""
       | Experimental [Verifiable Data Registry](https://github.com/hyperledger-identus/vdr) endpoints.
       |""".stripMargin

  val tag = Tag(tagName, Some(tagDescription))

  val readEntry: PublicEndpoint[
    (RequestContext, String),
    ErrorResponse,
    Array[Byte],
    Any
  ] = endpoint.get
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in("vdr" / "entries")
    .in(query[String]("url"))
    .out(byteArrayBody)
    .errorOut(EndpointOutputs.basicFailuresAndNotFound)
    .name("getVdrEntry")
    .summary("Resolve VDR entry")
    .tag(tagName)

  val createEntry: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, Array[Byte], QueryParams),
    ErrorResponse,
    CreateVdrEntryResponse,
    Any
  ] =
    endpoint.post
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("vdr" / "entries")
      .in(byteArrayBody)
      .in(queryParams)
      .out(statusCode(StatusCode.Created).description("Created a VDR entry"))
      .out(jsonBody[CreateVdrEntryResponse])
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("createVdrEntry")
      .summary("Create VDR entry")
      .tag(tagName)

}
