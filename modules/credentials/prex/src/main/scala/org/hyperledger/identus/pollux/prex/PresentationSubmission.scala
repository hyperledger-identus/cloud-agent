package org.hyperledger.identus.pollux.prex

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class InputDescriptorMapping(
    id: String,
    format: ClaimFormatValue,
    path: JsonPathValue,
    path_nested: Option[InputDescriptorMapping]
)

object InputDescriptorMapping {
  given JsonEncoder[InputDescriptorMapping] = DeriveJsonEncoder.gen
  given JsonDecoder[InputDescriptorMapping] = DeriveJsonDecoder.gen
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
  given JsonEncoder[PresentationSubmission] = DeriveJsonEncoder.gen
  given JsonDecoder[PresentationSubmission] = DeriveJsonDecoder.gen
}
