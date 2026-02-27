package org.hyperledger.identus.pollux.credentialschema.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.CredentialSchemaAndTrustedIssuersConstraint
import org.hyperledger.identus.pollux.credentialschema.http
import sttp.model.Uri
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import sttp.tapir.Validator.nonEmptyString
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonEncoder}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

//TODO: All these classes should be moved to the Pollux library into api package
case class VerificationPolicyConstraint(
    @description(VerificationPolicyConstraint.annotations.schemaId.description)
    @encodedExample(VerificationPolicyConstraint.annotations.schemaId.example)
    @validate(nonEmptyString)
    schemaId: String,
    @description(VerificationPolicyConstraint.annotations.trustedIssuers.description)
    @encodedExample(VerificationPolicyConstraint.annotations.trustedIssuers.example)
    trustedIssuers: Seq[String]
)

object VerificationPolicyConstraint {
  given encoder: zio.json.JsonEncoder[VerificationPolicyConstraint] =
    DeriveJsonEncoder.gen[VerificationPolicyConstraint]

  given decoder: zio.json.JsonDecoder[VerificationPolicyConstraint] =
    DeriveJsonDecoder.gen[VerificationPolicyConstraint]

  @encodedExample(JsonEncoder[VerificationPolicyConstraint].encodeJson(VerificationPolicyConstraint.example))
  given schema: Schema[VerificationPolicyConstraint] = Schema.derived

  extension (constraint: model.VerificationPolicyConstraint) {
    def toSchema(): VerificationPolicyConstraint = {
      VerificationPolicyConstraint(constraint)
    }
  }

  given Conversion[model.VerificationPolicyConstraint, VerificationPolicyConstraint] with {
    override def apply(constraint: model.VerificationPolicyConstraint): VerificationPolicyConstraint =
      VerificationPolicyConstraint(constraint)
  }

  def apply(constraint: model.VerificationPolicyConstraint): VerificationPolicyConstraint = {
    constraint match {
      case CredentialSchemaAndTrustedIssuersConstraint(schemaId, trustedIssuers) =>
        VerificationPolicyConstraint(schemaId, trustedIssuers)
    }
  }

  object annotations {
    object schemaId
        extends Annotation[String](
          description = "The schema ID of the credential that is being verified.",
          example = "https://example.com/driving-license-1.0"
        )

    object trustedIssuers
        extends Annotation[Seq[String]](
          description = "A list of DIDs of the trusted issuers.",
          example = Seq("did:example:123456789abcdefghi")
        )
  }

  val example = VerificationPolicyConstraint(
    schemaId = "https://example.com/driving-license-1.0",
    trustedIssuers = Seq("did:example:123456789abcdefghi")
  )
}

case class VerificationPolicyResponse(
    @description(VerificationPolicyResponse.annotations.self.description)
    @encodedExample(VerificationPolicyResponse.annotations.self.example)
    self: String,
    @description(VerificationPolicyResponse.annotations.kind.description)
    @encodedExample(VerificationPolicyResponse.annotations.kind.example)
    kind: String,
    @description(VerificationPolicyResponse.annotations.id.description)
    @encodedExample(VerificationPolicyResponse.annotations.id.example)
    id: UUID,
    @description(VerificationPolicyResponse.annotations.nonce.description)
    @encodedExample(VerificationPolicyResponse.annotations.nonce.example)
    nonce: Int = 0,
    @description(VerificationPolicyResponse.annotations.name.description)
    @encodedExample(VerificationPolicyResponse.annotations.name.example)
    @validate(nonEmptyString)
    name: String,
    @description(VerificationPolicyResponse.annotations.description.description)
    @encodedExample(VerificationPolicyResponse.annotations.description.example)
    description: String,
    @description(VerificationPolicyResponse.annotations.createdAt.description)
    @encodedExample(VerificationPolicyResponse.annotations.createdAt.example)
    createdAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    @description(VerificationPolicyResponse.annotations.updatedAt.description)
    @encodedExample(VerificationPolicyResponse.annotations.updatedAt.example)
    updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    @description(VerificationPolicyResponse.annotations.constraints.description)
    @encodedExample(
      JsonEncoder[Seq[VerificationPolicyConstraint]].encodeJson(
        VerificationPolicyResponse.annotations.constraints.example
      )
    )
    constraints: Seq[VerificationPolicyConstraint]
) {
  def update(in: VerificationPolicyInput): VerificationPolicyResponse = {
    copy(
      id = in.id.getOrElse(UUID.randomUUID()),
      name = in.name,
      description = in.description,
      constraints = in.constraints
    )
  }

  def withBaseUri(base: Uri) = withSelf(base.addPath(id.toString).toString)

  def withUri(uri: Uri) = withSelf(uri.toString)

  def withSelf(self: String) = copy(self = self)
}

object VerificationPolicyResponse {
  def apply(in: VerificationPolicyInput): VerificationPolicyResponse =
    VerificationPolicyResponse(
      self = "",
      kind = "VerificationPolicy",
      id = in.id.getOrElse(UUID.randomUUID()),
      name = in.name,
      description = in.description,
      constraints = in.constraints
    )

  given encoder: zio.json.JsonEncoder[VerificationPolicyResponse] = DeriveJsonEncoder.gen[VerificationPolicyResponse]
  given decoder: zio.json.JsonDecoder[VerificationPolicyResponse] = DeriveJsonDecoder.gen[VerificationPolicyResponse]

  @encodedExample(JsonEncoder[VerificationPolicyResponse].encodeJson(VerificationPolicyResponse.example))
  given schema: Schema[VerificationPolicyResponse] = Schema.derived

  import VerificationPolicyConstraint._
  extension (vp: model.VerificationPolicy) {
    def toSchema(): VerificationPolicyResponse = {
      VerificationPolicyResponse(
        self = "",
        kind = "VerificationPolicy",
        id = vp.id,
        nonce = vp.nonce,
        name = vp.name,
        description = vp.description,
        createdAt = vp.createdAt,
        updatedAt = vp.updatedAt,
        constraints = vp.constrains.map(VerificationPolicyConstraint(_)).toList
      )
    }
  }

  case class Filter(
      @description(VerificationPolicyResponse.annotations.name.description)
      @encodedExample(VerificationPolicyResponse.annotations.name.example)
      @validate(nonEmptyString)
      name: Option[String]
  )

  object annotations {
    object id
        extends Annotation[UUID](
          description =
            "A unique identifier to address the verification policy instance. UUID is generated by the backend.",
          example = UUID.fromString("0527aea1-d131-3948-a34d-03af39aba8b5")
        )

    object self
        extends Annotation[String](
          description = "The URL that uniquely identifies the resource being returned in the response.",
          example = "/cloud-agent/verification/policies/0527aea1-d131-3948-a34d-03af39aba8b4"
        )

    object kind
        extends Annotation[String](
          description = "A string that identifies the type of resource being returned in the response.",
          example = "VerificationPolicy"
        )

    object name
        extends Annotation[String](
          description = "A human-readable name for the verification policy. The `name` cannot be empty.",
          example = "Trusted Issuers Verification Policy"
        )

    object nonce
        extends Annotation[Int](
          description = "A number that is changed every time the verification policy is updated.",
          example = 1234
        )

    object description
        extends Annotation[String](
          description = "A human-readable description of the verification policy.",
          example = "Verification policy that checks if the credential was issued by a trusted issuer."
        )

    object createdAt
        extends Annotation[OffsetDateTime](
          description =
            "[RFC3339](https://www.rfc-editor.org/rfc/rfc3339) date on which the verification policy was created.",
          example = OffsetDateTime.parse("2022-03-10T12:00:00Z")
        )

    object updatedAt
        extends Annotation[OffsetDateTime](
          description =
            "[RFC3339](https://www.rfc-editor.org/rfc/rfc3339) date on which the verification policy was updated.",
          example = OffsetDateTime.parse("2022-03-10T12:00:00Z")
        )

    object constraints
        extends Annotation[Seq[VerificationPolicyConstraint]](
          description =
            "The object that describes the constraints of the verification policy. Each constraint is a tuple of the `schemaId` and a set of DIDs of the trusted issuers.",
          example = Seq[VerificationPolicyConstraint](
            VerificationPolicyConstraint(
              schemaId = "https://example.com/driving-license-1.0",
              trustedIssuers = Seq("did:example:123456789abcdefghi")
            )
          )
        )
  }

  val example = VerificationPolicyResponse(
    self = "/cloud-agent/verification/policies",
    kind = "VerificationPolicy",
    id = UUID.fromString("0527aea1-d131-3948-a34d-03af39aba8b4"),
    nonce = 0,
    name = "Trusted Issuers Verification Policy",
    description = "Verification policy that checks if the credential was issued by a trusted issuer.",
    createdAt = OffsetDateTime.parse("2022-03-10T12:00:00Z"),
    updatedAt = OffsetDateTime.parse("2022-03-10T12:00:00Z"),
    constraints = Seq(
      VerificationPolicyConstraint(
        schemaId = "https://example.com/driving-license-1.0",
        trustedIssuers = Seq("did:example:123456789abcdefghi")
      )
    )
  )
}

case class VerificationPolicyResponsePage(
    @description(VerificationPolicyResponsePage.annotations.self.description)
    @encodedExample(VerificationPolicyResponsePage.annotations.self.example)
    self: String,
    @description(VerificationPolicyResponsePage.annotations.kind.description)
    @encodedExample(VerificationPolicyResponsePage.annotations.kind.example)
    kind: String,
    @description(VerificationPolicyResponsePage.annotations.pageOf.description)
    @encodedExample(VerificationPolicyResponsePage.annotations.pageOf.example)
    pageOf: String,
    @description(VerificationPolicyResponsePage.annotations.next.description)
    @encodedExample(VerificationPolicyResponsePage.annotations.next.example)
    next: Option[String],
    @description(VerificationPolicyResponsePage.annotations.previous.description)
    @encodedExample(VerificationPolicyResponsePage.annotations.previous.example)
    previous: Option[String],
    @description(VerificationPolicyResponsePage.annotations.contents.description)
    @encodedExample(
      JsonEncoder[Seq[VerificationPolicyResponse]].encodeJson(
        VerificationPolicyResponsePage.annotations.contents.example
      )
    )
    contents: List[VerificationPolicyResponse]
)

object VerificationPolicyResponsePage {
  given encoder: zio.json.JsonEncoder[VerificationPolicyResponsePage] =
    DeriveJsonEncoder.gen[VerificationPolicyResponsePage]

  given decoder: zio.json.JsonDecoder[VerificationPolicyResponsePage] =
    DeriveJsonDecoder.gen[VerificationPolicyResponsePage]

  @encodedExample(JsonEncoder[VerificationPolicyResponsePage].encodeJson(VerificationPolicyResponsePage.example))
  given schema: Schema[VerificationPolicyResponsePage] =
    Schema.derived

  object annotations {
    object self
        extends Annotation[String](
          description = "The URL that uniquely identifies the resource being returned in the response.",
          example = "/cloud-agent/verification/policies?name=Trusted&offset=0&limit=10"
        )

    object kind
        extends Annotation[String](
          description = "A string that identifies the type of resource being returned in the response.",
          example = "VerificationPolicyPage"
        )

    object pageOf
        extends Annotation[String](
          description = "A string field indicating the type of resource that the contents field contains",
          example = "/cloud-agent/verification/policies"
        )

    object next
        extends Annotation[String](
          description = "An optional string field containing the URL of the next page of results. " +
            "If the API response does not contain any more pages, this field should be set to None.",
          example = "/cloud-agent/verification/policies?skip=20&limit=10"
        )

    object previous
        extends Annotation[String](
          description = "An optional string field containing the URL of the previous page of results. " +
            "If the API response is the first page of results, this field should be set to None.",
          example = "/cloud-agent/verification/policies?skip=0&limit=10"
        )
    object contents
        extends Annotation[Seq[VerificationPolicyResponse]](
          description =
            "A sequence of VerificationPolicyResponse objects representing the list of verification policies that the paginated response contains",
          example = Seq(VerificationPolicyResponse.example)
        )
  }

  val example = VerificationPolicyResponsePage(
    self = "/cloud-agent/verification/policies?name=Trusted&offset=0&limit=10",
    kind = "VerificationPolicyPage",
    pageOf = "/cloud-agent/verification/policies",
    next = Some("/cloud-agent/verification/policies?skip=20&limit=10"),
    previous = Some("/cloud-agent/verification/policies?skip=0&limit=10"),
    contents = List(VerificationPolicyResponse.example)
  )
}

case class VerificationPolicyInput(
    @description(VerificationPolicyResponse.annotations.id.description)
    @encodedExample(VerificationPolicyResponse.annotations.id.example)
    id: Option[UUID],
    @description(VerificationPolicyResponse.annotations.name.description)
    @encodedExample(VerificationPolicyResponse.annotations.name.example)
    @validate(nonEmptyString)
    name: String,
    @description(VerificationPolicyResponse.annotations.description.description)
    @encodedExample(VerificationPolicyResponse.annotations.description.example)
    description: String,
    @description(VerificationPolicyResponse.annotations.constraints.description)
    @encodedExample(
      JsonEncoder[Seq[VerificationPolicyConstraint]].encodeJson(
        VerificationPolicyResponse.annotations.constraints.example
      )
    )
    constraints: List[VerificationPolicyConstraint],
)

object VerificationPolicyInput {
  given encoder: zio.json.JsonEncoder[VerificationPolicyInput] =
    DeriveJsonEncoder.gen[VerificationPolicyInput]

  given decoder: zio.json.JsonDecoder[VerificationPolicyInput] =
    DeriveJsonDecoder.gen[VerificationPolicyInput]

  given schema: Schema[VerificationPolicyInput] = Schema.derived
}
