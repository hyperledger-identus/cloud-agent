package org.hyperledger.identus.connect.controller.http

import org.hyperledger.identus.api.http.{Annotation, ErrorResponse}
import org.hyperledger.identus.connect.controller.http.Connection.annotations
import org.hyperledger.identus.connect.core.model
import org.hyperledger.identus.connect.core.model.ConnectionRecord.Role
import sttp.model.Uri
import sttp.tapir.{Schema, Validator}
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

case class Connection(
    @description(annotations.connectionId.description)
    @encodedExample(annotations.connectionId.example)
    connectionId: UUID,
    @description(annotations.thid.description)
    @encodedExample(annotations.thid.example)
    thid: String,
    @description(annotations.label.description)
    @encodedExample(annotations.label.example)
    label: Option[String] = None,
    @description(annotations.goalcode.description)
    @encodedExample(annotations.goalcode.example)
    goalCode: Option[String] = None,
    @description(annotations.goal.description)
    @encodedExample(annotations.goal.example)
    goal: Option[String] = None,
    @description(annotations.myDid.description)
    @encodedExample(annotations.myDid.example)
    myDid: Option[String] = None,
    @description(annotations.theirDid.description)
    @encodedExample(annotations.theirDid.example)
    theirDid: Option[String] = None,
    @description(annotations.role.description)
    @encodedExample(annotations.role.example)
    @validate(annotations.role.validator)
    role: String,
    @description(annotations.state.description)
    @encodedExample(annotations.state.example)
    @validate(annotations.state.validator)
    state: String,
    @description(annotations.invitation.description)
    invitation: ConnectionInvitation,
    @description(annotations.createdAt.description)
    @encodedExample(annotations.createdAt.example)
    createdAt: OffsetDateTime,
    @description(annotations.updatedAt.description)
    @encodedExample(annotations.updatedAt.example)
    updatedAt: Option[OffsetDateTime] = None,
    @description(annotations.metaRetries.description)
    @encodedExample(annotations.metaRetries.example)
    metaRetries: Int,
    @description(annotations.metaLastFailure.description)
    @encodedExample(annotations.metaLastFailure.example)
    metaLastFailure: Option[ErrorResponse] = None,
    @description(annotations.self.description)
    @encodedExample(annotations.self.example)
    self: String = "",
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String = "Connection"
) {
  def withBaseUri(base: Uri): Connection = withSelf(base.addPath(connectionId.toString).toString)
  def withSelf(self: String): Connection = copy(self = self)
}

object Connection {

  def fromDomain(domain: model.ConnectionRecord): Connection =
    Connection(
      connectionId = domain.id,
      thid = domain.thid,
      label = domain.label,
      goalCode = domain.goalCode,
      goal = domain.goal,
      myDid = domain.role match
        case Role.Inviter =>
          domain.connectionResponse.map(_.from).orElse(domain.connectionRequest.map(_.to)).map(_.value)
        case Role.Invitee =>
          domain.connectionResponse.map(_.to).orElse(domain.connectionRequest.map(_.from)).map(_.value)
      ,
      theirDid = domain.role match
        case Role.Inviter =>
          domain.connectionResponse.map(_.to).orElse(domain.connectionRequest.map(_.from)).map(_.value)
        case Role.Invitee =>
          domain.connectionResponse.map(_.from).orElse(domain.connectionRequest.map(_.to)).map(_.value)
      ,
      role = domain.role.toString,
      state = domain.protocolState.toString,
      invitation = ConnectionInvitation.fromDomain(domain.invitation),
      createdAt = domain.createdAt.atOffset(ZoneOffset.UTC),
      updatedAt = domain.updatedAt.map(_.atOffset(ZoneOffset.UTC)),
      metaRetries = domain.metaRetries,
      metaLastFailure = domain.metaLastFailure.map(failure => ErrorResponse.failureToErrorResponseConversion(failure)),
      self = domain.id.toString,
      kind = "Connection",
    )

  given Conversion[model.ConnectionRecord, Connection] = fromDomain

  object annotations {
    object connectionId
        extends Annotation[UUID](
          description = "The unique identifier of the connection.",
          example = UUID.fromString("0527aea1-d131-3948-a34d-03af39aba8b4")
        )

    object thid
        extends Annotation[String](
          description = "The unique identifier of the thread this connection record belongs to. " +
            "The value will identical on both sides of the connection (inviter and invitee)",
          example = "0527aea1-d131-3948-a34d-03af39aba8b4"
        )

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
    object myDid
        extends Annotation[String](
          description = "The DID representing me as the inviter or invitee in this specific connection.",
          example = "did:peer:12345"
        )

    object theirDid
        extends Annotation[String](
          description = "The DID representing the other peer as the an inviter or invitee in this specific connection.",
          example = "did:peer:67890"
        )

    object role
        extends Annotation[String](
          description = "The role played by the Prism agent in the connection flow.",
          example = "Inviter",
          validator = Validator.enumeration(
            List(
              "Inviter",
              "Invitee"
            )
          )
        )

    object state
        extends Annotation[String](
          description = "The current state of the connection protocol execution.",
          example = "InvitationGenerated",
          validator = Validator.enumeration(
            List(
              "InvitationGenerated",
              "InvitationReceived",
              "ConnectionRequestPending",
              "ConnectionRequestSent",
              "ConnectionRequestReceived",
              "ConnectionResponsePending",
              "ConnectionResponseSent",
              "ConnectionResponseReceived",
              "ProblemReportPending",
              "ProblemReportSent",
              "ProblemReportReceived"
            )
          )
        )

    object invitation
        extends Annotation[ConnectionInvitation](
          description = "The invitation for this connection",
          example = ConnectionInvitation.Example
        )

    object createdAt
        extends Annotation[OffsetDateTime](
          description = "The date and time the connection record was created.",
          example = OffsetDateTime.parse("2022-03-10T12:00:00Z")
        )

    object updatedAt
        extends Annotation[OffsetDateTime](
          description = "The date and time the connection record was last updated.",
          example = OffsetDateTime.parse("2022-03-10T12:00:00Z")
        )

    object metaRetries
        extends Annotation[Int](
          description = "The maximum background processing attempts remaining for this record",
          example = 5
        )

    object metaLastFailure
        extends Annotation[ErrorResponse](
          description = "The last failure if any.",
          example = ErrorResponse.example
        )

    object self
        extends Annotation[String](
          description = "The reference to the connection resource.",
          example = "https://atala-prism-products.io/connections/ABCD-1234"
        )

    object kind
        extends Annotation[String](
          description = "The type of object returned. In this case a `Connection`.",
          example = "Connection"
        )
  }

  given encoder: JsonEncoder[Connection] =
    DeriveJsonEncoder.gen[Connection]

  given decoder: JsonDecoder[Connection] =
    DeriveJsonDecoder.gen[Connection]

  given schema: Schema[Connection] = Schema.derived

}
