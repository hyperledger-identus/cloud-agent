package org.hyperledger.identus.issue.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import IssueCredentialOfferInvitation.annotations

import java.util.UUID

case class IssueCredentialOfferInvitation(
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: UUID,
    @description(annotations.`type`.description)
    @encodedExample(annotations.`type`.example)
    `type`: String,
    @description(annotations.from.description)
    @encodedExample(annotations.from.example)
    from: String,
    @description(annotations.invitationUrl.description)
    @encodedExample(annotations.invitationUrl.example)
    invitationUrl: String
)

object IssueCredentialOfferInvitation {

  def fromDomain(invitation: Invitation) = IssueCredentialOfferInvitation(
    id = UUID.fromString(invitation.id),
    `type` = invitation.`type`,
    from = invitation.from.value,
    invitationUrl = s"https://my.domain.com/path?_oob=${invitation.toBase64}"
  )

  object annotations {
    object id
        extends Annotation[UUID](
          description =
            "The unique identifier of the invitation. It should be used as parent thread ID (pthid) for the Connection Request message that follows.",
          example = UUID.fromString("0527aea1-d131-3948-a34d-03af39aba8b4")
        )

    object `type`
        extends Annotation[String](
          description = "The DIDComm Message Type URI (MTURI) the invitation message complies with.",
          example = "https://didcomm.org/out-of-band/2.0/invitation"
        )

    object from
        extends Annotation[String](
          description = "The DID representing the sender to be used by recipients for future interactions.",
          example = "did:peer:1234457"
        )

    object invitationUrl
        extends Annotation[String](
          description =
            "The invitation message encoded as a URL. This URL follows the Out of [Band 2.0 protocol](https://identity.foundation/didcomm-messaging/spec/v2.0/#out-of-band-messages) and can be used to generate a QR code for example.",
          example =
            "https://my.domain.com/path?_oob=eyJpZCI6ImY5NmUzNjk5LTU5MWMtNGFlNy1iNWU2LTZlZmU2ZDI2MjU1YiIsInR5cGUiOiJodHRwczovL2RpZGNvbW0ub3JnL291dC1vZi1iYW5kLzIuMC9pbnZpdGF0aW9uIiwiZnJvbSI6ImRpZDpwZWVyOjIuRXo2TFNmc0tNZTh2U1NXa1lkWkNwbjRZVmlQRVJmZEdBaGRMQUdIZ3gyTEdKd2ZtQS5WejZNa3B3MWtTYWJCTXprQTN2NTl0UUZuaDNGdGtLeTZ4TGhMeGQ5UzZCQW9hQmcyLlNleUowSWpvaVpHMGlMQ0p6SWpwN0luVnlhU0k2SW1oMGRIQTZMeTh4T1RJdU1UWTRMakV1TXpjNk9EQTRNQzlrYVdSamIyMXRJaXdpY2lJNlcxMHNJbUVpT2xzaVpHbGtZMjl0YlM5Mk1pSmRmWDAiLCJib2R5Ijp7ImdvYWxfY29kZSI6Imlzc3VlLXZjIiwiZ29hbCI6IlRvIGlzc3VlIGEgRmFiZXIgQ29sbGVnZSBHcmFkdWF0ZSBjcmVkZW50aWFsIiwiYWNjZXB0IjpbImRpZGNvbW0vdjIiXX0sImF0dGFjaG1lbnRzIjpbeyJpZCI6IjcwY2RjOTBjLTlhOTktNGNkYS04N2ZlLTRmNGIyNTk1MTEyYSIsIm1lZGlhX3R5cGUiOiJhcHBsaWNhdGlvbi9qc29uIiwiZGF0YSI6eyJqc29uIjp7ImlkIjoiNjU1ZTlhMmMtNDhlZC00NTliLWIzZGEtNmIzNjg2NjU1NTY0IiwidHlwZSI6Imh0dHBzOi8vZGlkY29tbS5vcmcvaXNzdWUtY3JlZGVudGlhbC8zLjAvb2ZmZXItY3JlZGVudGlhbCIsImJvZHkiOnsiZ29hbF9jb2RlIjoiT2ZmZXIgQ3JlZGVudGlhbCIsImNyZWRlbnRpYWxfcHJldmlldyI6eyJ0eXBlIjoiaHR0cHM6Ly9kaWRjb21tLm9yZy9pc3N1ZS1jcmVkZW50aWFsLzMuMC9jcmVkZW50aWFsLWNyZWRlbnRpYWwiLCJib2R5Ijp7ImF0dHJpYnV0ZXMiOlt7Im5hbWUiOiJmYW1pbHlOYW1lIiwidmFsdWUiOiJXb25kZXJsYW5kIn0seyJuYW1lIjoiZ2l2ZW5OYW1lIiwidmFsdWUiOiJBbGljZSJ9LHsibmFtZSI6ImRyaXZpbmdDbGFzcyIsInZhbHVlIjoiTXc9PSIsIm1lZGlhX3R5cGUiOiJhcHBsaWNhdGlvbi9qc29uIn0seyJuYW1lIjoiZGF0ZU9mSXNzdWFuY2UiLCJ2YWx1ZSI6IjIwMjAtMTEtMTNUMjA6MjA6MzkrMDA6MDAifSx7Im5hbWUiOiJlbWFpbEFkZHJlc3MiLCJ2YWx1ZSI6ImFsaWNlQHdvbmRlcmxhbmQuY29tIn0seyJuYW1lIjoiZHJpdmluZ0xpY2Vuc2VJRCIsInZhbHVlIjoiMTIzNDUifV19fX0sImF0dGFjaG1lbnRzIjpbeyJpZCI6Ijg0MDQ2NzhiLTlhMzYtNDk4OS1hZjFkLTBmNDQ1MzQ3ZTBlMyIsIm1lZGlhX3R5cGUiOiJhcHBsaWNhdGlvbi9qc29uIiwiZGF0YSI6eyJqc29uIjp7Im9wdGlvbnMiOnsiY2hhbGxlbmdlIjoiYWQwZjQzYWQtODUzOC00MWQ0LTljYjgtMjA5NjdiYzY4NWJjIiwiZG9tYWluIjoiZG9tYWluIn0sInByZXNlbnRhdGlvbl9kZWZpbml0aW9uIjp7ImlkIjoiNzQ4ZWZhNTgtMmJjZS00NDBkLTkyMWYtMjUyMGE4NDQ2NjYzIiwiaW5wdXRfZGVzY3JpcHRvcnMiOltdLCJmb3JtYXQiOnsiand0Ijp7ImFsZyI6WyJFUzI1NksiXSwicHJvb2ZfdHlwZSI6W119fX19fSwiZm9ybWF0IjoicHJpc20vand0In1dLCJ0aGlkIjoiZjk2ZTM2OTktNTkxYy00YWU3LWI1ZTYtNmVmZTZkMjYyNTViIiwiZnJvbSI6ImRpZDpwZWVyOjIuRXo2TFNmc0tNZTh2U1NXa1lkWkNwbjRZVmlQRVJmZEdBaGRMQUdIZ3gyTEdKd2ZtQS5WejZNa3B3MWtTYWJCTXprQTN2NTl0UUZuaDNGdGtLeTZ4TGhMeGQ5UzZCQW9hQmcyLlNleUowSWpvaVpHMGlMQ0p6SWpwN0luVnlhU0k2SW1oMGRIQTZMeTh4T1RJdU1UWTRMakV1TXpjNk9EQTRNQzlrYVdSamIyMXRJaXdpY2lJNlcxMHNJbUVpT2xzaVpHbGtZMjl0YlM5Mk1pSmRmWDAifX19XSwiY3JlYXRlZF90aW1lIjoxNzI0ODUxMTM5LCJleHBpcmVzX3RpbWUiOjE3MjQ4NTE0Mzl9="
        )
  }

  val Example = IssueCredentialOfferInvitation(
    id = annotations.id.example,
    `type` = annotations.`type`.example,
    from = annotations.from.example,
    invitationUrl = annotations.invitationUrl.example
  )

  given encoder: JsonEncoder[IssueCredentialOfferInvitation] =
    DeriveJsonEncoder.gen[IssueCredentialOfferInvitation]

  given decoder: JsonDecoder[IssueCredentialOfferInvitation] =
    DeriveJsonDecoder.gen[IssueCredentialOfferInvitation]

  given schema: Schema[IssueCredentialOfferInvitation] = Schema.derived
}
