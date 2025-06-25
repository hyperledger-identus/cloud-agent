package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.EndpointOutputs
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.api.http.RequestContext
import sttp.apispec.Tag
import sttp.tapir.*

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
    .name("getVdrEntry")
    .summary("Resolve VDR entry")
    .out(byteArrayBody)
    .errorOut(EndpointOutputs.basicFailuresAndNotFound)
    .tag(tagName)

}
