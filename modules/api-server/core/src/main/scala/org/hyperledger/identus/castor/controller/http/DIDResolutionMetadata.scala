package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.controller.http.DIDResolutionMetadata.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

@description("[DID resolution metadata](https://www.w3.org/TR/did-core/#did-resolution-metadata)")
final case class DIDResolutionMetadata(
    @description(annotations.error.description)
    @encodedExample(annotations.error.example)
    error: Option[String] = None,
    @description(annotations.errorMessage.description)
    @encodedExample(annotations.errorMessage.example)
    errorMessage: Option[String] = None,
    @description(annotations.contentType.description)
    @encodedExample(annotations.contentType.example)
    contentType: Option[String] = None
)

object DIDResolutionMetadata {

  object annotations {
    object error
        extends Annotation[String](
          description =
            "Resolution error constant according to [DID spec registries](https://www.w3.org/TR/did-spec-registries/#error)",
          example = "invalidDid"
        )

    object errorMessage
        extends Annotation[String](
          description = "Resolution error message",
          example = "The initialState does not match the suffix"
        )

    object contentType
        extends Annotation[String](
          description = "The media type of the returned DID document",
          example = "application/did+ld+json"
        )
  }

  given encoder: JsonEncoder[DIDResolutionMetadata] = DeriveJsonEncoder.gen[DIDResolutionMetadata]
  given decoder: JsonDecoder[DIDResolutionMetadata] = DeriveJsonDecoder.gen[DIDResolutionMetadata]
  given schema: Schema[DIDResolutionMetadata] = Schema.derived
}
