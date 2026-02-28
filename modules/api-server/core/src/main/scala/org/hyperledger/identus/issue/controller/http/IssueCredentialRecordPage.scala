package org.hyperledger.identus.issue.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.issue.controller.http.IssueCredentialRecordPage.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/** @param self
  *   The reference to the connection collection itself. for example: ''https://atala-prism-products.io/dids''
  * @param kind
  *   The type of object returned. In this case a `Collection`. for example: ''Collection''
  * @param pageOf
  *   Page number within the context of paginated response. for example: ''null''
  * @param next
  *   URL of the next page (if available) for example: ''null''
  * @param previous
  *   URL of the previous page (if available) for example: ''null''
  * @param contents
  *   for example: ''null''
  */
final case class IssueCredentialRecordPage(
    @description(annotations.contents.description)
    @encodedExample(annotations.contents.example)
    contents: Seq[IssueCredentialRecord],
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String,
    @description(annotations.self.description)
    @encodedExample(annotations.self.example)
    self: String,
    @description(annotations.pageOf.description)
    @encodedExample(annotations.pageOf.example)
    pageOf: String,
    @description(annotations.next.description)
    @encodedExample(annotations.next.example)
    next: Option[String] = None,
    @description(annotations.previous.description)
    @encodedExample(annotations.previous.example)
    previous: Option[String] = None
)

object IssueCredentialRecordPage {

  val Example = IssueCredentialRecordPage(
    contents = annotations.contents.example,
    kind = annotations.kind.example,
    self = annotations.self.example,
    pageOf = annotations.pageOf.example,
    next = Some(annotations.next.example),
    previous = Some(annotations.previous.example)
  )

  object annotations {

    object contents
        extends Annotation[Seq[IssueCredentialRecord]](
          description = """
          |An sequence of IssueCredentialRecord resources representing the list of credential records that the paginated response contains.
          |""".stripMargin,
          example = Seq.empty
        )

    object kind
        extends Annotation[String](
          description = "A string that identifies the type of resource being returned in the response.",
          example = "Collection"
        )

    object self
        extends Annotation[String](
          description = "The URL that uniquely identifies the resource being returned in the response.",
          example = "/cloud-agent/issue-credentials/records?offset=10&limit=10"
        )

    object pageOf
        extends Annotation[String](
          description = "A string field indicating the type of resource that the contents field contains.",
          example = "/cloud-agent/issue-credentials/records"
        )

    object next
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the next page of results. If the API response does not contain any more pages, this field should be set to None.",
          example = "/cloud-agent/issue-credentials/records?offset=20&limit=10"
        )

    object previous
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the previous page of results. If the API response is the first page of results, this field should be set to None.",
          example = "/cloud-agent/issue-credentials/records?offset=0&limit=10"
        )
  }

  given encoder: JsonEncoder[IssueCredentialRecordPage] =
    DeriveJsonEncoder.gen[IssueCredentialRecordPage]

  given decoder: JsonDecoder[IssueCredentialRecordPage] =
    DeriveJsonDecoder.gen[IssueCredentialRecordPage]

  given schema: Schema[IssueCredentialRecordPage] = Schema.derived

}
