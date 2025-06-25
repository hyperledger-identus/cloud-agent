package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.api.http.RequestContext
import sttp.apispec.Tag
import sttp.tapir.*

object VdrEndpoints {

  private val tagName = "VDR"
  private val tagDescription =
    s"""
    | Experimental [Verifiable Data Registry](https://github.com/hyperledger-identus/vdr) endpoints
    """

  val tag = Tag(tagName, Some(tagDescription))

  val readData = endpoint.get
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in("vdrs" / "data")
    .in(query[String]("vdr-url"))
    .name("getVdrData")
    .out(byteArrayBody)
    .tag(tagName)

}
