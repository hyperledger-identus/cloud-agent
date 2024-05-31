package org.hyperledger.identus.iam.entity.http.model

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.iam.entity.http.model.ApiKeyAuthenticationRequest.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.description
import sttp.tapir.Schema.annotations.encodedExample
import sttp.tapir.Schema.annotations.validate
import sttp.tapir.Schema.annotations.validateEach
import sttp.tapir.Validator
import sttp.tapir.Validator.*
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

import java.util.UUID

case class ApiKeyAuthenticationRequest(
    @description(annotations.entityId.description)
    @encodedExample(annotations.entityId.example)
    entityId: UUID,
    @description(annotations.apikey.description)
    @encodedExample(annotations.apikey.example)
    @validate(all(minLength(16), maxLength(128)))
    apiKey: String
)

object ApiKeyAuthenticationRequest {
  given encoder: JsonEncoder[ApiKeyAuthenticationRequest] =
    DeriveJsonEncoder.gen[ApiKeyAuthenticationRequest]

  given decoder: JsonDecoder[ApiKeyAuthenticationRequest] =
    DeriveJsonDecoder.gen[ApiKeyAuthenticationRequest]

  given schema: Schema[ApiKeyAuthenticationRequest] = Schema.derived

  object annotations {

    object entityId
        extends Annotation[UUID](
          description = "The `entityId` of the entity to be updated",
          example = UUID.fromString("01234567-0000-0000-0000-000000000000")
        )

    object apikey
        extends Annotation[String](
          description = "The `apikey` of the entity to be updated",
          example = "dkflks3DflkFmkllnDfde"
        )
  }
}
