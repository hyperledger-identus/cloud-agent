package org.hyperledger.identus.pollux.core.model

import sttp.tapir.Schema
import zio.json.*

enum ResourceResolutionMethod {
  case did
  case http
}

object ResourceResolutionMethod {
  given schema: Schema[ResourceResolutionMethod] = Schema.derivedEnumeration.defaultStringBased
  given encoder: JsonEncoder[ResourceResolutionMethod] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[ResourceResolutionMethod] = JsonDecoder[String].mapOrFail { s =>
    ResourceResolutionMethod.values.find(_.toString == s).toRight(s"Unknown ResourceResolutionMethod: $s")
  }
}
