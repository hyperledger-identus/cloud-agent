package org.hyperledger.identus.vdr.controller.http

import org.hyperledger.identus.api.http.Annotation
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class UpdateVdrEntryResponse(
    @description(UpdateVdrEntryResponse.annotations.url.description)
    @encodedExample(UpdateVdrEntryResponse.annotations.url.example)
    url: Option[String]
)

object UpdateVdrEntryResponse {
  given encoder: JsonEncoder[UpdateVdrEntryResponse] = DeriveJsonEncoder.gen[UpdateVdrEntryResponse]
  given decoder: JsonDecoder[UpdateVdrEntryResponse] = DeriveJsonDecoder.gen[UpdateVdrEntryResponse]
  given schema: Schema[UpdateVdrEntryResponse] = Schema.derived

  object annotations {
    object url
        extends Annotation[String](
          description = "A VDR url that can be used to locate the resource",
          example = "http://host-a/?drf=memory&drid=memory&drv=0.1.0&m=0#a8bddddf-4894-437c-8d23-7b6f08ef766a"
        )
  }
}
