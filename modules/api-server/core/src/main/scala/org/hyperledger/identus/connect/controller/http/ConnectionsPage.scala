package org.hyperledger.identus.connect.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.connect.controller.http.ConnectionsPage.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ConnectionsPage(
    @description(annotations.contents.description)
    @encodedExample(annotations.contents.example)
    contents: Seq[Connection],
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String = "ConnectionsPage",
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
)

object ConnectionsPage {

  val Example = ConnectionsPage(
    contents = annotations.contents.example,
    kind = annotations.kind.example,
    self = annotations.self.example,
    pageOf = annotations.pageOf.example,
    next = Some(annotations.next.example),
    previous = Some(annotations.previous.example)
  )

  object annotations {
    object contents
        extends Annotation[Seq[Connection]](
          description = """
          |Array of resources (Connection)
          |A sequence of Connection resources representing the list of connections that the paginated response contains.
          """.stripMargin,
          example = Seq.empty
        )

    object kind
        extends Annotation[String](
          description = "A string that identifies the type of resource being returned in the response.",
          example = "ConnectionsPage"
        )

    object self
        extends Annotation[String](
          description = "The URL that uniquely identifies the resource being returned in the response.",
          example = "/cloud-agent/connections?offset=10&limit=10"
        )

    object pageOf
        extends Annotation[String](
          description = "A string field indicating the type of resource that the contents field contains.",
          example = ""
        )

    object next
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the next page of results. If the API response does not contain any more pages, this field should be set to None.",
          example = "/cloud-agent/connections?offset=20&limit=10"
        )

    object previous
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the previous page of results. If the API response is the first page of results, this field should be set to None.",
          example = "/cloud-agent/connections?offset=0&limit=10"
        )
  }

  given encoder: JsonEncoder[ConnectionsPage] =
    DeriveJsonEncoder.gen[ConnectionsPage]

  given decoder: JsonDecoder[ConnectionsPage] =
    DeriveJsonDecoder.gen[ConnectionsPage]

  given schema: Schema[ConnectionsPage] = Schema.derived
}
