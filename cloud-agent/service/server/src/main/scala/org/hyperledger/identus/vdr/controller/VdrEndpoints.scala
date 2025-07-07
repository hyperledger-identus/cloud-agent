package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.{EndpointOutputs, ErrorResponse, RequestContext}
import org.hyperledger.identus.vdr.controller.http.{CreateVdrEntryResponse, Proof, UpdateVdrEntryResponse}
import sttp.apispec.Tag
import sttp.model.{QueryParams, StatusCode}
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
    (RequestContext, Array[Byte], QueryParams, Option[String], Option[String], Option[String]),
    ErrorResponse,
    CreateVdrEntryResponse,
    Any
  ] =
    endpoint.post
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("vdr" / "entries")
      .in(byteArrayBody)
      .in(queryParams)
      // Explit query for documentation purpose. Internally, raw query is passed to the VDR.
      .in(query[Option[String]]("drf"))
      .in(query[Option[String]]("drid"))
      .in(query[Option[String]]("drv"))
      .out(statusCode(StatusCode.Created).description("Created a VDR entry"))
      .out(jsonBody[CreateVdrEntryResponse])
      .errorOut(EndpointOutputs.basicFailuresAndForbidden)
      .name("createVdrEntry")
      .summary("Create VDR entry")
      .description("Create a new VDR entry from the request body by the driver specified in the query parameters")
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
      .out(statusCode(StatusCode.Ok).description("Updated a VDR entry"))
      .out(jsonBody[UpdateVdrEntryResponse])
      .errorOut(EndpointOutputs.basicFailuresAndNotFound)
      .name("updateVdrEntry")
      .summary("Update VDR entry")
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
      .errorOut(EndpointOutputs.basicFailuresAndNotFound)
      .name("deleteVdrEntry")
      .summary("Delete VDR entry")
      .tag(tagName)

  val entryProof: PublicEndpoint[
    (RequestContext, String),
    ErrorResponse,
    Proof,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("vdr" / "proofs")
      .in(query[String]("url"))
      .out(jsonBody[Proof])
      .out(statusCode(StatusCode.Ok).description("Proof of a VDR entry"))
      .errorOut(EndpointOutputs.basicFailuresAndNotFound)
      .name("vdrEntryProof")
      .summary("Get a proof of VDR entry")
      .tag(tagName)

}
