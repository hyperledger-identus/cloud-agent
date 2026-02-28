package org.hyperledger.identus.presentproof.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.presentproof.controller.http.Options.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class Options(
    @description(annotations.challenge.description)
    @encodedExample(annotations.challenge.example)
    challenge: String,
    @description(annotations.domain.description)
    @encodedExample(annotations.domain.example)
    domain: String
)

object Options {
  object annotations {
    object challenge
        extends Annotation[String](
          description = "The challenge should be a randomly generated string.",
          example = "11c91493-01b3-4c4d-ac36-b336bab5bddf"
        )
    object domain
        extends Annotation[String](
          description = "The domain value can be any string or URI.",
          example = "https://example-verifier.com"
        )

    val Example: Options = Options(annotations.challenge.example, annotations.domain.example)
  }

  given encoder: JsonEncoder[Options] =
    DeriveJsonEncoder.gen[Options]

  given decoder: JsonDecoder[Options] =
    DeriveJsonDecoder.gen[Options]

  given schema: Schema[Options] = Schema.derived
}
