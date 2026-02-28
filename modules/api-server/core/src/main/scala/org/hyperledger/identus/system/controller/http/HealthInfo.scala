package org.hyperledger.identus.system.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.system.controller.http.HealthInfo.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/** A class to represent response detailing health of service including version
  *
  * @param version
  *   The current version number of the running application
  */
final case class HealthInfo(
    @description(annotations.version.description)
    @encodedExample(annotations.version.example)
    version: String
)

object HealthInfo {
  object annotations {

    object version
        extends Annotation[String](
          description = "The semantic version number of the running service",
          example = "1.1.0"
        )

  }

  given encoder: JsonEncoder[HealthInfo] =
    DeriveJsonEncoder.gen[HealthInfo]

  given decoder: JsonDecoder[HealthInfo] =
    DeriveJsonDecoder.gen[HealthInfo]

  given schema: Schema[HealthInfo] = Schema.derived

}
