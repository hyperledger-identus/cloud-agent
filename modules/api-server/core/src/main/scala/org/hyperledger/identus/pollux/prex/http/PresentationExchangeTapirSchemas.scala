package org.hyperledger.identus.pollux.prex.http

import org.hyperledger.identus.pollux.prex.*
import sttp.tapir.Schema

import scala.language.implicitConversions

object PresentationExchangeTapirSchemas {
  given Schema[PresentationDefinition] = Schema
    .derived[PresentationDefinition]
    .description(
      "*Presentation Definition* object according to the [PresentationExchange spec](https://identity.foundation/presentation-exchange/spec/v2.1.1/#presentation-definition)"
    )
  given Schema[InputDescriptor] = Schema.derived
  given Schema[ClaimFormat] = Schema.derived
  given Schema[Constraints] = Schema.derived
  given Schema[Jwt] = Schema.derived
  given Schema[Ldp] = Schema.derived
  given Schema[Field] = Schema.derived
  given Schema[JsonPathValue] = Schema.schemaForString.map[JsonPathValue](Some(_))(_.value)
  given Schema[FieldFilter] = Schema.any[FieldFilter]
}
