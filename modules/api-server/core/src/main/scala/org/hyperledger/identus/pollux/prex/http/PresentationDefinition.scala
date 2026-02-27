package org.hyperledger.identus.pollux.prex.http

import org.hyperledger.identus.pollux.prex.{ClaimFormat, InputDescriptor, PresentationDefinition}
import org.hyperledger.identus.pollux.prex.http.PresentationExchangeTapirSchemas.given
import sttp.tapir.Schema
import zio.json.{JsonDecoder, JsonEncoder}

case class CreatePresentationDefinition(
    input_descriptors: Seq[InputDescriptor] = Seq.empty,
    name: Option[String] = None,
    purpose: Option[String] = None,
    format: Option[ClaimFormat] = None
)

object CreatePresentationDefinition {
  given Schema[CreatePresentationDefinition] = Schema.derived
  given JsonEncoder[CreatePresentationDefinition] = JsonEncoder.derived
  given JsonDecoder[CreatePresentationDefinition] = JsonDecoder.derived

  given Conversion[CreatePresentationDefinition, PresentationDefinition] = cpd =>
    PresentationDefinition(
      input_descriptors = cpd.input_descriptors,
      name = cpd.name,
      purpose = cpd.purpose,
      format = cpd.format
    )
}

case class PresentationDefinitionPage(
    self: String,
    kind: String = "PresentationDefinitionPage",
    pageOf: String,
    next: Option[String] = None,
    previous: Option[String] = None,
    contents: Seq[PresentationDefinition]
)

object PresentationDefinitionPage {
  given Schema[PresentationDefinitionPage] = Schema.derived
  given JsonEncoder[PresentationDefinitionPage] = JsonEncoder.derived
  given JsonDecoder[PresentationDefinitionPage] = JsonDecoder.derived
}
