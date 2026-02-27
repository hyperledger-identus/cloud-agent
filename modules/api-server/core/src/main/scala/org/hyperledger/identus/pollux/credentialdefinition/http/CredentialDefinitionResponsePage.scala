package org.hyperledger.identus.pollux.credentialdefinition.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.pollux.credentialschema.http.CredentialSchemaResponsePage.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class CredentialDefinitionResponsePage(
    @description(annotations.contents.description)
    @encodedExample(annotations.contents.example)
    contents: Seq[CredentialDefinitionResponse],
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String = "CredentialDefinitionPage",
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
  def withSelf(self: String) = copy(self = self)
}

object CredentialDefinitionResponsePage {
  given encoder: JsonEncoder[CredentialDefinitionResponsePage] =
    DeriveJsonEncoder.gen[CredentialDefinitionResponsePage]

  given decoder: JsonDecoder[CredentialDefinitionResponsePage] =
    DeriveJsonDecoder.gen[CredentialDefinitionResponsePage]

  given schema: Schema[CredentialDefinitionResponsePage] = Schema.derived

  val Example = CredentialDefinitionResponsePage(
    contents = annotations.contents.example,
    kind = annotations.kind.example,
    self = annotations.self.example,
    pageOf = annotations.pageOf.example,
    next = Some(annotations.next.example),
    previous = Some(annotations.previous.example)
  )

  object annotations {

    object contents
        extends Annotation[Seq[CredentialDefinitionResponse]](
          description =
            "A sequence of CredentialDefinitionResponse objects representing the list of credential definitions that the API response contains",
          example = Seq.empty
        )

    object kind
        extends Annotation[String](
          description =
            "A string field indicating the type of the API response. In this case, it will always be set to `CredentialDefinitionPage`",
          example = "CredentialDefinitionPage"
        ) // TODO Tech Debt ticket - the kind in a collection should be collection, not the underlying record type

    object self
        extends Annotation[String](
          description = "A string field containing the URL of the current API endpoint",
          example = "/cloud-agent/credential-definition-registry/definitions?skip=10&limit=10"
        )

    object pageOf
        extends Annotation[String](
          description = "A string field indicating the type of resource that the contents field contains",
          example = "/cloud-agent/credential-definition-registry/definitions"
        )

    object next
        extends Annotation[String](
          description = "An optional string field containing the URL of the next page of results. " +
            "If the API response does not contain any more pages, this field should be set to None.",
          example = "/cloud-agent/credential-definition-registry/definitions?skip=20&limit=10"
        )

    object previous
        extends Annotation[String](
          description = "An optional string field containing the URL of the previous page of results. " +
            "If the API response is the first page of results, this field should be set to None.",
          example = "/cloud-agent/credential-definition-registry/definitions?skip=0&limit=10"
        )
  }
}
