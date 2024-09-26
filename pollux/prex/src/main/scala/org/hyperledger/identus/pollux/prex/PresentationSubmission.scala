package org.hyperledger.identus.pollux.prex

import io.circe.*
import io.circe.generic.semiauto.*
import zio.json.{JsonDecoder, JsonEncoder}

case class InputDescriptorMapping(
    id: String,
    format: ClaimFormatValue,
    path: JsonPathValue,
    path_nested: Option[InputDescriptorMapping]
)

object InputDescriptorMapping {
  given Encoder[InputDescriptorMapping] = deriveEncoder[InputDescriptorMapping]
  given Decoder[InputDescriptorMapping] = deriveDecoder[InputDescriptorMapping]

  given JsonEncoder[InputDescriptorMapping] = JsonEncoder.derived
  given JsonDecoder[InputDescriptorMapping] = JsonDecoder.derived
}

/** Refer to <a
  * href="https://identity.foundation/presentation-exchange/spec/v2.1.1/#presentation-submission">Presentation
  * Definition</a>
  */
case class PresentationSubmission(
    definition_id: String,
    id: String = java.util.UUID.randomUUID.toString(), // UUID
    descriptor_map: Seq[InputDescriptorMapping] = Seq.empty
)

object PresentationSubmission {
  given Encoder[PresentationSubmission] = deriveEncoder[PresentationSubmission]
  given Decoder[PresentationSubmission] = deriveDecoder[PresentationSubmission]

  given JsonEncoder[PresentationSubmission] = JsonEncoder.derived
  given JsonDecoder[PresentationSubmission] = JsonDecoder.derived
}
