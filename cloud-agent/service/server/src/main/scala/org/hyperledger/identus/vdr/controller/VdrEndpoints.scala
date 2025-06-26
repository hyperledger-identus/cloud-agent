package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.EndpointOutputs
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.vdr.controller.http.CreateVdrEntryResponse
import org.hyperledger.identus.vdr.controller.http.UpdateVdrEntryResponse
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
       |
       | VDR interface is a public proxy interface to interact with various VDR drivers.
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
    .out(statusCode(StatusCode.Ok).description("Read a VDR entry successfully"))
    .out(byteArrayBody)
    .errorOut(EndpointOutputs.basicFailuresAndNotFound)
    .name("getVdrEntry")
    .summary("Resolve VDR entry")
    .tag(tagName)

  val createEntry: PublicEndpoint[
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
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("createVdrEntry")
      .summary("Create VDR entry")
      .tag(tagName)

  val updateEntry: PublicEndpoint[
    (RequestContext, String, Array[Byte], QueryParams),
    ErrorResponse,
    UpdateVdrEntryResponse,
    Any
  ] =
    endpoint.put
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("vdr" / "entries")
      .in(query[String]("url"))
      .in(byteArrayBody)
      .in(queryParams)
      .out(statusCode(StatusCode.Created).description("Created a VDR entry"))
      .out(jsonBody[UpdateVdrEntryResponse])
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("createVdrEntry")
      .summary("Create VDR entry")
      .tag(tagName)

  val deleteEntry: PublicEndpoint[
    (RequestContext, String, QueryParams),
    ErrorResponse,
    Unit,
    Any
  ] =
    endpoint.delete
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("vdr" / "entries")
      .in(query[String]("url"))
      .in(queryParams)
      .out(statusCode(StatusCode.Ok).description("Deleted a VDR entry"))
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("deleteVdrEntry")
      .summary("Delete VDR entry")
      .tag(tagName)

}
