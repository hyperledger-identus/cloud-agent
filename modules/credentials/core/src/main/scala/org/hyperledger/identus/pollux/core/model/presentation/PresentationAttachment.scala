package org.hyperledger.identus.pollux.core.model.presentation

import org.hyperledger.identus.pollux.prex.PresentationDefinition
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class Options(challenge: String, domain: String)
object Options {
  given JsonEncoder[Options] = DeriveJsonEncoder.gen
  given JsonDecoder[Options] = DeriveJsonDecoder.gen
}

case class PresentationAttachment(options: Option[Options] = None, presentation_definition: PresentationDefinition)
object PresentationAttachment {
  given JsonEncoder[PresentationAttachment] = DeriveJsonEncoder.gen
  given JsonDecoder[PresentationAttachment] = DeriveJsonDecoder.gen

  def build(options: Option[Options] = None): PresentationAttachment = {
    val presentationDefinition =
      PresentationDefinition(input_descriptors = Seq.empty)
    PresentationAttachment(options, presentationDefinition)
  }
}
