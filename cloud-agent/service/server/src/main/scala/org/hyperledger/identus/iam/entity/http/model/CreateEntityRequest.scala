package org.hyperledger.identus.iam.entity.http.model

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.iam.entity.http.model.CreateEntityRequest.annotations
import sttp.tapir.{Schema, Validator}
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.util.UUID

case class CreateEntityRequest(
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: Option[UUID],
    @description(annotations.name.description)
    @encodedExample(annotations.name.example)
    @validate(annotations.name.validator)
    name: String,
    @description(annotations.walletId.description)
    @encodedExample(annotations.walletId.example)
    walletId: Option[UUID]
)

object CreateEntityRequest {
  given encoder: JsonEncoder[CreateEntityRequest] =
    DeriveJsonEncoder.gen[CreateEntityRequest]

  given decoder: JsonDecoder[CreateEntityRequest] =
    DeriveJsonDecoder.gen[CreateEntityRequest]

  given schema: Schema[CreateEntityRequest] = Schema.derived

  object annotations {

    object id
        extends Annotation[UUID](
          description =
            "The new `id` of the entity to be created. If this field is not provided, the server will generate a new UUID for the entity",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )

    object name
        extends Annotation[String](
          description =
            "The new `name` of the entity to be created. If this field is not provided, the server will generate a random name for the entity",
          example = "John Doe",
          validator = Validator.all(Validator.nonEmptyString, Validator.maxLength(128))
        )

    object walletId
        extends Annotation[UUID](
          description =
            "The new `walletId` of the entity to be created. If this field is not provided, the server will set the default `walletId`",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )
  }
}
