package org.hyperledger.identus.iam.entity.http.model

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.iam.entity.http.model.EntityResponse.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import sttp.tapir.Validator.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.Instant
import java.util.UUID

case class EntityResponse(
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String,
    @description(annotations.self.description)
    @encodedExample(annotations.self.example)
    self: String,
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: UUID,
    @description(annotations.name.description)
    @encodedExample(annotations.name.example)
    name: String,
    @description(annotations.walletId.description)
    @encodedExample(annotations.walletId.example)
    walletId: UUID,
    @description(annotations.createdAt.description)
    @encodedExample(annotations.createdAt.example)
    createdAt: Instant,
    @description(annotations.updatedAt.description)
    @encodedExample(annotations.updatedAt.example)
    updatedAt: Instant
) {
  def withSelf(self: String): EntityResponse = copy(self = self)
}

object EntityResponse {
  def fromDomain(entity: Entity): EntityResponse =
    EntityResponse(
      kind = "Entity",
      self = "",
      id = entity.id,
      name = entity.name,
      walletId = entity.walletId,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  given encoder: JsonEncoder[EntityResponse] =
    DeriveJsonEncoder.gen[EntityResponse]

  given decoder: JsonDecoder[EntityResponse] =
    DeriveJsonDecoder.gen[EntityResponse]

  given schema: Schema[EntityResponse] = Schema.derived

  object annotations {

    object kind
        extends Annotation[String](
          description = "The `kind` of the entity.",
          example = "Entity"
        )

    object self
        extends Annotation[String](
          description = "The `self` link of the entity.",
          example = "http://localhost:8080/cloud-agent/iam/entities/00000000-0000-0000-0000-000000000000"
        )

    object id
        extends Annotation[UUID](
          description = "The unique `id` of the entity",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )

    object name
        extends Annotation[String](
          description = "The `name` of the entity.",
          example = "John Doe"
        )

    object walletId
        extends Annotation[UUID](
          description = "The `walletId` owned by the entity.",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )

    object createdAt
        extends Annotation[Instant](
          description = "The `createdAt` timestamp of the entity.",
          example = Instant.parse("2023-01-01T00:00:00Z")
        )

    object updatedAt
        extends Annotation[Instant](
          description = "The `updatedAt` timestamp of the entity.",
          example = Instant.parse("2023-01-01T00:00:00Z")
        )
  }

  val Example = EntityResponse(
    kind = "Entity",
    self = "/cloud-agent/iam/entities/00000000-0000-0000-0000-000000000000",
    id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    name = "John Doe",
    walletId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    createdAt = Instant.parse("2023-01-01T00:00:00Z"),
    updatedAt = Instant.parse("2023-01-01T00:00:00Z")
  )
}
