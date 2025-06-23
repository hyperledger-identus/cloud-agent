package org.hyperledger.identus.pollux

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.pollux.PrismEnvelopeResponse.annotations
import org.hyperledger.identus.shared.models.PrismEnvelope
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.*

case class PrismEnvelopeResponse(
    @description(annotations.resource.description)
    @encodedExample(annotations.resource.example)
    resource: String,
    @description(annotations.resource.description)
    @encodedExample(annotations.url.example)
    url: String
) extends PrismEnvelope

object PrismEnvelopeResponse {
  given encoder: JsonEncoder[PrismEnvelopeResponse] =
    DeriveJsonEncoder.gen[PrismEnvelopeResponse]

  given decoder: JsonDecoder[PrismEnvelopeResponse] =
    DeriveJsonDecoder.gen[PrismEnvelopeResponse]

  given schema: Schema[PrismEnvelopeResponse] = Schema.derived

  object annotations {
    object resource
        extends Annotation[String](
          description = "JCS normalized and base64url encoded json of the resource",
          example = "" // TODO Add example
        )

    object url
        extends Annotation[String](
          description = "DID url that can be used to resolve this resource",
          example =
            "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a?resourceService=agent-base-url&resourcePath=credential-definition-registry/definitions/did-url/ef3e4135-8fcf-3ce7-b5bb-df37defc13f6?resourceHash=4074bb1a8e0ea45437ad86763cd7e12de3fe8349ef19113df773b0d65c8a9c46"
        )
  }
}
