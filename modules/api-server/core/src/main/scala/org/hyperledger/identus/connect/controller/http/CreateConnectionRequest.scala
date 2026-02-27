package org.hyperledger.identus.connect.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.connect.controller.http.CreateConnectionRequest.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class CreateConnectionRequest(
    @description(annotations.label.description)
    @encodedExample(annotations.label.example)
    label: Option[String] = None,
    @description(annotations.goalcode.description)
    @encodedExample(annotations.goalcode.example)
    goalCode: Option[String] = None,
    @description(annotations.goal.description)
    @encodedExample(annotations.goal.example)
    goal: Option[String] = None
)

object CreateConnectionRequest {

  object annotations {
    object label
        extends Annotation[String](
          description = "A human readable alias for the connection.",
          example = "Peter"
        )
    object goalcode
        extends Annotation[String](
          description =
            "A self-attested code the receiver may want to display to the user or use in automatically deciding what to do with the out-of-band message.",
          example = "issue-vc"
        )
    object goal
        extends Annotation[String](
          description =
            "A self-attested string that the receiver may want to display to the user about the context-specific goal of the out-of-band message.",
          example = "To issue a Faber College Graduate credential"
        )
  }

  given encoder: JsonEncoder[CreateConnectionRequest] =
    DeriveJsonEncoder.gen[CreateConnectionRequest]

  given decoder: JsonDecoder[CreateConnectionRequest] =
    DeriveJsonDecoder.gen[CreateConnectionRequest]

  given schema: Schema[CreateConnectionRequest] = Schema.derived

}
