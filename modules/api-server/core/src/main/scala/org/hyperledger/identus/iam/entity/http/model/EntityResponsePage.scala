package org.hyperledger.identus.iam.entity.http.model

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.iam.entity.http.model.EntityResponsePage.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import sttp.tapir.Validator.*
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class EntityResponsePage(
    @description(annotations.contents.description)
    @encodedExample( // This is a hammer - to be improved in the future
      JsonEncoder[Seq[EntityResponse]].encodeJson(annotations.contents.example)
    )
    contents: Seq[EntityResponse],
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String = "EntityResponsePage",
    @description(annotations.self.description)
    @encodedExample(annotations.self.example)
    self: String = "",
    @description(annotations.pageOf.description)
    @encodedExample(annotations.pageOf.example)
    pageOf: String = "",
    @description(annotations.next.description)
    @encodedExample(annotations.next.example)
    next: Option[String] = None,
    @description(annotations.previous.description)
    @encodedExample(annotations.previous.example)
    previous: Option[String] = None
) {
  def withSelf(self: String): EntityResponsePage =
    this.copy(self = self)
}

object EntityResponsePage {

  def fromDomain(entities: Seq[Entity]): EntityResponsePage =
    EntityResponsePage(
      contents = entities.map(EntityResponse.fromDomain),
      self = "",
      pageOf = "",
      next = None,
      previous = None
    )
  given encoder: JsonEncoder[EntityResponsePage] =
    DeriveJsonEncoder.gen[EntityResponsePage]

  given decoder: JsonDecoder[EntityResponsePage] =
    DeriveJsonDecoder.gen[EntityResponsePage]

  given schema: Schema[EntityResponsePage] = Schema.derived

  object annotations {

    object contents
        extends Annotation[Seq[EntityResponse]](
          description =
            "A sequence of CredentialSchemaResponse objects representing the list of credential schemas that the API response contains",
          example = Seq(EntityResponse.Example)
        )

    object kind
        extends Annotation[String](
          description =
            "A string field indicating the type of the API response. In this case, it will always be set to `CredentialSchemaPage`",
          example = "CredentialSchemaPage"
        ) // TODO Tech Debt ticket - the kind in a collection should be collection, not the underlying record type

    object self
        extends Annotation[String](
          description = "A string field containing the URL of the current API endpoint",
          example = "/cloud-agent/schema-registry/schemas?skip=10&limit=10"
        )

    object pageOf
        extends Annotation[String](
          description = "A string field indicating the type of resource that the contents field contains",
          example = "/cloud-agent/schema-registry/schemas"
        )

    object next
        extends Annotation[String](
          description = "An optional string field containing the URL of the next page of results. " +
            "If the API response does not contain any more pages, this field should be set to None.",
          example = "/cloud-agent/schema-registry/schemas?skip=20&limit=10"
        )

    object previous
        extends Annotation[String](
          description = "An optional string field containing the URL of the previous page of results. " +
            "If the API response is the first page of results, this field should be set to None.",
          example = "/cloud-agent/schema-registry/schemas?skip=0&limit=10"
        )
  }

}
