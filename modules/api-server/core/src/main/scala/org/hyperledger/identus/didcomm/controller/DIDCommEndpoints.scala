package org.hyperledger.identus.didcomm.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.EndpointOutputs.{basicFailuresWith, FailureVariant}
import org.hyperledger.identus.didcomm.controller.http.DIDCommMessage
import sttp.tapir.*
import sttp.tapir.json.zio.jsonBody

object DIDCommEndpoints {
  val handleDIDCommMessage: PublicEndpoint[
    (RequestContext, DIDCommMessage),
    ErrorResponse,
    Unit,
    Any
  ] = endpoint.post
    .in(extractFromRequest[RequestContext](RequestContext.apply))
    .in(jsonBody[DIDCommMessage])
    .in("")
    .out(emptyOutput)
    .errorOut(basicFailuresWith(FailureVariant.unprocessableEntity, FailureVariant.notFound))
}
