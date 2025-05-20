package org.hyperledger.identus.issue.controller.http

import org.hyperledger.identus.api.http.{Annotation, ErrorResponse}
import org.hyperledger.identus.issue.controller.http.IssueCredentialRecord.annotations
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, Base64}
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord as PolluxIssueCredentialRecord
import sttp.tapir.{Schema, Validator}
import sttp.tapir.json.zio.schemaForZioJsonValue
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import zio.json.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets
import java.time.{OffsetDateTime, ZoneOffset}

final case class IssueCredentialRecord(
    @description(annotations.recordId.description)
    @encodedExample(annotations.recordId.example)
    recordId: String,
    @description(annotations.thid.description)
    @encodedExample(annotations.thid.example)
    thid: String,
    @description(annotations.credentialFormat.description)
    @encodedExample(annotations.credentialFormat.example)
    @validate(annotations.credentialFormat.validator)
    credentialFormat: String,
    @description(annotations.subjectId.description)
    @encodedExample(annotations.subjectId.example)
    subjectId: Option[String] = None,
    @description(annotations.validityPeriod.description)
    @encodedExample(annotations.validityPeriod.example)
    validityPeriod: Option[Double] = None,
    @description(annotations.claims.description)
    @encodedExample(annotations.claims.example)
    claims: zio.json.ast.Json,
    @description(annotations.automaticIssuance.description)
    @encodedExample(annotations.automaticIssuance.example)
    automaticIssuance: Option[Boolean] = None,
    @description(annotations.createdAt.description)
    @encodedExample(annotations.createdAt.example)
    createdAt: OffsetDateTime,
    @description(annotations.updatedAt.description)
    @encodedExample(annotations.updatedAt.example)
    updatedAt: Option[OffsetDateTime] = None,
    @description(annotations.role.description)
    @encodedExample(annotations.role.example)
    @validate(annotations.role.validator)
    role: String,
    @description(annotations.protocolState.description)
    @encodedExample(annotations.protocolState.example)
    @validate(annotations.protocolState.validator)
    protocolState: String,
    @description(annotations.credential.description)
    @encodedExample(annotations.credential.example)
    credential: Option[String] = None,
    @description(annotations.issuingDID.description)
    @encodedExample(annotations.issuingDID.example)
    issuingDID: Option[String] = None,
    @description(annotations.goalcode.description)
    @encodedExample(annotations.goalcode.example)
    goalCode: Option[String] = None,
    @description(annotations.goal.description)
    @encodedExample(annotations.goal.example)
    goal: Option[String] = None,
    @description(annotations.myDid.description)
    @encodedExample(annotations.myDid.example)
    myDid: Option[String] = None,
    @description(annotations.invitation.description)
    invitation: Option[IssueCredentialOfferInvitation] = None,
    @description(annotations.metaRetries.description)
    @encodedExample(annotations.metaRetries.example)
    metaRetries: Int,
    @description(annotations.metaLastFailure.description)
    @encodedExample(annotations.metaLastFailure.example)
    metaLastFailure: Option[ErrorResponse] = None
)

object IssueCredentialRecord {

  def fromDomain(domain: PolluxIssueCredentialRecord): IssueCredentialRecord =
    IssueCredentialRecord(
      recordId = domain.id.value,
      thid = domain.thid.value,
      createdAt = domain.createdAt.atOffset(ZoneOffset.UTC),
      updatedAt = domain.updatedAt.map(_.atOffset(ZoneOffset.UTC)),
      role = domain.role.toString,
      credentialFormat = domain.credentialFormat.toString,
      subjectId = domain.subjectId,
      claims = domain.offerCredentialData
        .map(offer =>
          offer.body.credential_preview.body.attributes
            .foldLeft(Json.Obj()) { case (jsObject, attr) =>
              val jsonValue = attr.media_type match
                case Some("application/json") =>
                  val jsonString =
                    String(java.util.Base64.getUrlDecoder.decode(attr.value.getBytes(StandardCharsets.UTF_8)))
                  jsonString.fromJson[Json].getOrElse(Json.Str(s"Unsupported VC claims value: $jsonString"))
                case Some(mime) => Json.Str(s"Unsupported 'media_type': $mime")
                case None       => Json.Str(attr.value)
              jsObject.copy(fields = jsObject.fields.appended(attr.name -> jsonValue))
            }
        )
        .getOrElse(Json.Null),
      validityPeriod = domain.validityPeriod,
      automaticIssuance = domain.automaticIssuance,
      protocolState = domain.protocolState.toString,
      credential = domain.issueCredentialData.flatMap(issueCredential => {
        issueCredential.attachments.collectFirst { case AttachmentDescriptor(_, _, Base64(vc), _, _, _, _, _) =>
          vc
        }
      }),
      invitation = domain.invitation.map(invitation => IssueCredentialOfferInvitation.fromDomain(invitation)),
      goalCode = domain.invitation.flatMap(_.body.goal_code),
      goal = domain.invitation.flatMap(_.body.goal),
      myDid = domain.invitation.map(_.from.value),
      metaRetries = domain.metaRetries,
      metaLastFailure = domain.metaLastFailure.map(failure => ErrorResponse.failureToErrorResponseConversion(failure)),
    )

  given Conversion[PolluxIssueCredentialRecord, IssueCredentialRecord] = fromDomain

  object annotations {

    object subjectId
        extends Annotation[Option[String]](
          description = """
          |The short-form subject Prism DID to which the JWT verifiable credential will be or has been issued.
          |This parameter only applies if the offer is of type 'JWT' and will only exist in the cloud agent of the holder (it will be empty on the issuer side).
          |""".stripMargin,
          example = Some("did:prism:3bb0505d13fcb04d28a48234edb27b0d4e6d7e18a81e2c1abab58f3bbc21ce6f")
        )

    object validityPeriod
        extends Annotation[Double](
          description = """
          |The validity period in seconds of the verifiable credential that will be issued.
          |This parameter will only exist in the cloud agent of the issuer (it will be empty on the holder side).
          |""".stripMargin,
          example = 3600
        )

    object claims
        extends Annotation[zio.json.ast.Json](
          description = """
          |The set of claims included in the issued credential.
          |""".stripMargin,
          example = zio.json.ast.Json.Obj(
            "firstname" -> zio.json.ast.Json.Str("Alice"),
            "lastname" -> zio.json.ast.Json.Str("Wonderland"),
          )
        )

    object automaticIssuance
        extends Annotation[Boolean](
          description = """
            |Specifies whether or not the credential is automatically generated and issued when receiving the `CredentialRequest` from the holder.
            |If set to `false`, a manual approval by the issuer via another API call will be required for the VC to be issued.
            |This parameter will only exist in the cloud agent of the issuer (it will be empty on the holder side).
            |""".stripMargin,
          example = true
        )

    object recordId
        extends Annotation[String](
          description = """
          |The unique identifier of the issue credential record.
          |This identifier is internal to the agent and not shared between issuer and holder.
          |""".stripMargin,
          example = "80d612dc-0ded-4ac9-90b4-1b8eabb04545"
        )

    object thid
        extends Annotation[String](
          description = """
          |The unique identifier of the 'thread' identifying the specific issuance flow execution as a whole.
          |This same unique 'thid' value is included in every message exchanged during the flow execution.
          |It is shared between the issuer and the holder agents and its value identical on both sides.
          |""".stripMargin,
          example = "0527aea1-d131-3948-a34d-03af39aba8b4"
        )

    object credentialFormat
        extends Annotation[String](
          description = "The credential format for this offer.",
          example = "JWT",
          validator = Validator.enumeration(
            List(
              "JWT",
              "SDJWT",
              "AnonCreds"
            )
          )
        )

    object createdAt
        extends Annotation[OffsetDateTime](
          description = "The date and time when the issue credential record was created.",
          example = OffsetDateTime.parse("2023-01-01T00:00:00Z")
        )

    object updatedAt
        extends Annotation[Option[OffsetDateTime]](
          description = "The date and time when the issue credential record was last updated.",
          example = None
        )

    object role
        extends Annotation[String](
          description = "The role played by the agent in the credential issuance flow.",
          example = "Issuer",
          validator = Validator.enumeration(
            List(
              "Issuer",
              "Holder"
            )
          )
        )

    object protocolState
        extends Annotation[String](
          description = "The current state of the issue credential protocol execution.",
          example = "CredentialSent",
          validator = Validator.enumeration(
            List(
              "OfferPending",
              "OfferSent",
              "OfferReceived",
              "RequestPending",
              "RequestGenerated",
              "RequestSent",
              "RequestReceived",
              "CredentialPending",
              "CredentialGenerated",
              "CredentialSent",
              "CredentialReceived",
              "ProblemReportPending",
              "ProblemReportSent",
              "ProblemReportReceived",
              "InvitationExpired",
              "InvitationGenerated",
            )
          )
        )

    object credential
        extends Annotation[Option[String]](
          description =
            "The base64-encoded credential that was issued by the issuer agent, in 'JWT' or 'AnonCreds' format depending on the offer type.",
          example = Some(
            "eyJzY2hlbWFfaWQiOiJodHRwOi8vaG9zdC5kb2NrZXIuaW50ZXJuYWw6ODA4MC9wcmlzbS1hZ2VudC9zY2hlbWEtcmVnaXN0cnkvc2NoZW1hcy8zOTZmZDE2OC02YmVmLTMyNDItYTJiNy1hNTZlYWM1MDc2OWMvc2NoZW1hIiwiY3JlZF9kZWZfaWQiOiJodHRwOi8vMTkyLjE2OC4wLjE0OjgwODAvcHJpc20tYWdlbnQvY3JlZGVudGlhbC1kZWZpbml0aW9uLXJlZ2lzdHJ5L2RlZmluaXRpb25zLzNhZmQxZWJkLWIzN2ItMzRiNC1iMWQ2LWYwMDQ5ZmU5ZmQ1Mi9kZWZpbml0aW9uIiwicmV2X3JlZ19pZCI6bnVsbCwidmFsdWVzIjp7ImdpdmVuTmFtZSI6eyJyYXciOiJBbGljZSIsImVuY29kZWQiOiIyNzAzNDY0MDAyNDExNzMzMTAzMzA2MzEyODA0NDAwNDMxODIxODQ4NjgxNjkzMTUyMDg4NjQwNTUzNTY1OTkzNDQxNzQzODc4MTUwNyJ9LCJlbWFpbEFkZHJlc3MiOnsicmF3IjoiYWxpY2VAd29uZGVybGFuZC5jb20iLCJlbmNvZGVkIjoiNzUxMDcwNDYzNDAxNjU2NzcwMTE5NDIwNzU2NDQwMDkwNjY1NDE2NjExNDg4MjI1ODkwMzM2Nzk4NjEyMDkxODY0OTI3Njg2Njk5MjQifSwiZmFtaWx5TmFtZSI6eyJyYXciOiJXb25kZXJsYW5kIiwiZW5jb2RlZCI6IjE2NzkwODQ5MzEyMzc0Nzk0NzM2ODEzMzc3NTY3MjUzODUxMzczNjA3OTcwNDczMzc3NzAxNDc3MjY5MTk0MDE5NTU3NjU0NTYyMDM1In0sImRhdGVPZklzc3VhbmNlIjp7InJhdyI6IjIwMjAtMTEtMTNUMjA6MjA6MzkrMDA6MDAiLCJlbmNvZGVkIjoiNTM4Njg1NTk1MzE3NDg0NjcwOTc1MjA4NTkwNTMwODE4MzU3NDc0MzU2MTE2MDY4NDIwNDExNDc1ODIwMDQ4NzQzNDgwNDYxNjQ2ODUifSwiZHJpdmluZ0xpY2Vuc2VJRCI6eyJyYXciOiIxMjM0NSIsImVuY29kZWQiOiIxMjM0NSJ9LCJkcml2aW5nQ2xhc3MiOnsicmF3IjoiMyIsImVuY29kZWQiOiIzIn19LCJzaWduYXR1cmUiOnsicF9jcmVkZW50aWFsIjp7Im1fMiI6IjMzNjg4ODUzNTU3NTg2MDI3MDg3OTY5ODAzMjgzMzcyNzE4Nzc5MDAzNDAzMDgwODMzMjQzNDIxMTU3MDA5NzE4MDUzMTMyNDIwODAwIiwiYSI6IjEwMjUxMTg2OTU5MTg2NDc2NDcwNzU0MTQ0MDg5NDE3MjI4OTM1Mjk0ODgxNDExMTc5ODYwNzgxODIxODY2OTcyODIyMzg1MTQ1OTcwNDA4Mzk2Mjg5OTM2NzgzNTUxMDk4NDA2MjE2MjcwNjgyNDM1ODg3NjY0OTI0MzQwMDg3NTY4MDMyNzMyMzYwMDc5MTI2ODk2MDU3NDA3MTYyMjI0NDgwODM2NTgzNjY2MzQ1MzA5NzQ0NDE5NjA0ODg5ODA1NDU3Mjc4NDE0MjgyMjA4NzIzMDIwNDQzNzk0MjM0NzU1NTgwNjA1MTE1NjU3NTQ4NjE1MTgwNTU1ODEzMjA0MzQyNjkzNjYyODQzNzY4MjQ2NDM1NjU4MjQ5MDYyMjUxMzYwNzE2MzEyNzM4MjAyMTU2NTEwNzM2NDY1ODk2NjIyNDY4MDk3OTY0OTk0NTA1NDUwMjczMzQ2Mzk4MzY4NzcxNDM3MzAzNTI2NjE0NTk4NTU4Mjg0MTAxNzk0NjYwOTAxNDMwOTI4MzY1MTk3MzA2MDIxMzQ5OTQ3MDI2MzIzMzEwOTE3MjgzODM0ODY2NzI1MzgyMDg4NDIzNDU1NzE0MDY3MTk1NDEzMDA4MzAxNTQ2MTA1NzY4NTAxNzMxNjEwMjk3MDY5ODUyNjAxMTgxMTM3OTg2NjM2MDU2MjI4MTE4NzUzMTM1NjMxMDIwNzA0MzYxODQxNTg0MjA0NzIwMDU1NjY0ODIxMTczOTA3MzYyMTQzNTQyNjk1NTExMTMxNzU3NTE0OTUxMDY2ODQ2MzIyMDAyNzYxMzg4MzIwNjkyNSIsImUiOiIyNTkzNDQ3MjMwNTUwNjIwNTk5MDcwMjU0OTE0ODA2OTc1NzE5MzgyNzc4ODk1MTUxNTIzMDYyNDk3Mjg1ODMxMDU2NjU4MDA3MTMzMDY3NTkxNDk5ODE2OTA1NTkxOTM5ODcxNDMwMTIzNjc5MTMyMDYyOTkzMjM4OTk2OTY5NDIyMTMyMzU5NTY3NDI5Mjk4MTYwMTkzMjA4MDYyNzM5NTc5MDExNDE1Njk0NDAwMjUyMTkiLCJ2IjoiMTAwODYwMTE5NjExNDQ0MjUxODg1ODcyNzA0OTEyNDQwMTQzNTA0MDEwMDQ3NzE3MzYxNzgxMjIwOTQ0OTA3OTE3ODM0NTE3NjQyOTk5MDgxMjEzMDcyNTI4NzU5NTczMzIyNDM1NjU5NzY4OTI2NzA4MjE0NTI5Njg0Njg5NTc5MjAwNzY4MDkxMTM0OTM0MzYxNDUyNzM5ODUwMjEyODc1MDUwMzg5NjkzOTMxMjEzMDg2MTUyOTM4NzA2ODc4MzQyNjIxNjQ1MTc2NDY5NTU0NDMyNTY2MDk0MDY5NjU2ODkzNDg1NjQyNDI2MTc0MjA5MjY3OTI1MDEzODkxMTU2MzAzMzY0MzUwMzgwNTUwMzQ4OTk3MDI1Nzc3NDc5NDg3ODI0NDkzNzg4MDYwNTg1NzMxMTY2NDM5OTE1MTc3ODUyNTYwNjczMjkwODA2Mjk5ODEyOTY1NjMwNDc2OTc0NzExNDY4MDE1MzY4NzM2NTc3MDEzNDE2NjE3ODc0MTc3ODgwNjMzNTc1OTAwMzQyODM5MDUxNjc4NjExNDMxMTk5Mzk3NTIzMDE5Njc0NTA3MjM0NDAzNzcyMTcxMDM3Nzg3NzUyNTMwMjIyODYyNDg5OTMzMzczMzY1MjIwNTc4MDIzNDY2NjkyMDQ4MTA0NTE0NjczMzMwMzMwNzQ1OTEyMjUzNzQ0MDQwMjI1NTM4NjMxNDk2MjY4NDM0MDk5Njk3Nzk1NTY5MDA3MTExMjQzMTg4MTc3MzUyNjE5MTUxNzk1NjEzNTAwNjg5MzUwMzQyNTk3NjA1ODY2MjUyOTYwMjQ3ODg4OTE2NDIwNDcyMDEzNDYzMTA5NTA5MjMxNDcwNjc4MDc5MDI2Mzc3MzY4NDEwNTIyODg3NDExOTIyMzE4Njk5NzA4MjkxNzI4NTg3ODgzNjExODMyMjU4MTE5MzI2ODQ5NjkxODI1MDI2MzU2NDQ1OTM1NjYxOTkyODEyNjIwNDY4MzAxMjEwMzMwNTA1NjEwMjYyNTU5MDk5NDgwNzcxMjA0NDU0ODg0MDI5ODA3MDcwOTM4NDU5OTgxNDM1NjQyNTkzNTQyODc0ODAifSwicl9jcmVkZW50aWFsIjpudWxsfSwic2lnbmF0dXJlX2NvcnJlY3RuZXNzX3Byb29mIjp7InNlIjoiMjMwNDc5NjEzNTA0MDI5NTI3NTk3NzM3MTY4NjY0OTQ5MzQwNzk1NTg1ODM5NTQ4OTI2MTEwMjQ2NzU0NzA3OTgyNjc2MDEyMjIyMTYyNzQyNTQ2Nzg4NDI3MDA4MDQ5NzIyNDMwNDgyODAyOTYyNTgxNDE2ODI2MjEzMTgwMTE4MTA4MTA1Nzg1NjA4OTg5NjEyNTU0ODMwOTE5MjU4MTI0NDgyMzUwNTQ2MTkxOTQ5NTU5ODM2NTk4NzcxMTE0MzI1NjA5MTI4MjUxMTc1MDM4NDMxNDA2NzM2NTc1MDkwMzk5ODk0MDQzMjc3MTg5MTM3MzE1NTM5NTQ2MTE3Mjk2NTM1OTMyOTQ3NDk3NTU0Mjg0NDc1NjkxNDE1NDEzMzIzNjE2OTYyNTk1NDAzMTkxMjQ4ODY2NDE1MjI0NDY2MTU2OTgyODg4OTkyNTAxNjc1NTcwNjI0MzQ2MzMyMTE0NjMzMDQ1NzUxNDg3NzU3ODM3MDA5Mzc3ODMwNTI1MTU5MjUwNjMwMjcxNjY3NDQxMDI3MTM5MjE3Nzc5ODU5MDExMjUxOTc1OTczNjY0NTc4MjMxOTk1Mzc3OTE4Mjg4MTkyNjIyMDM5NDEzMDM0ODg5MjM3Mzg2NzU4Mzg3NTcwNTMxNDc1OTQxMDU2MTg3NzUzOTEyNDA0NzkwNzQ5NzgzMTM0OTk3MDgzODk3NjE5MTczMTg3MDg1MzE1MjQ3NTM4NjU2OTcwOTE0NzI2MzM1ODA1ODY5OTk3NzI3OTc5NTUwMjIzNjkzMDA2MjcwNjIzNTc3NjM2NTIyNjIyNTY0MTE4NTMiLCJjIjoiOTM2NzQ1MDczNzcxNzQ3MjE3OTg3OTY2OTYzMDQxNzUzMTE4NDk0NDE4NDQ0NzQ1MDI3NzAyMjI3Nzk0NzU1ODQ2Mjg3ODMzMzU4NTAifSwicmV2X3JlZyI6bnVsbCwid2l0bmVzcyI6bnVsbH0="
          )
        )

    object issuingDID
        extends Annotation[Option[String]](
          description = """
          |The short-form issuer Prism DID by which the JWT verifiable credential will be or has been issued.
          |Note that this parameter only applies when the offer is type 'JWT'.
          |""".stripMargin,
          example = Some("did:prism:3bb0505d13fcb04d28a48234edb27b0d4e6d7e18a81e2c1abab58f3bbc21ce6f")
        )

    object goalcode
        extends Annotation[String](
          description = """
              |A self-attested code the receiver may want to display to the user or use in automatically deciding what to do with the out-of-band message.
              |The goalCode is optional and can be included when the credential offer originates from an invitation for connectionless issuance
              |""".stripMargin,
          example = "issue-vc"
        )

    object goal
        extends Annotation[String](
          description = """
              |A self-attested string that the receiver may want to display to the user about the context-specific goal of the out-of-band message.
              |The goal is optional and can be included when the credential offer originates from an invitation for connectionless issuance
              |""".stripMargin,
          example = "To issue a Faber College Graduate credential"
        )

    object myDid
        extends Annotation[String](
          description = "The DID representing me as the inviter or invitee in this specific connection.",
          example = "did:peer:12345"
        )

    object invitation
        extends Annotation[IssueCredentialOfferInvitation](
          description = "The invitation for this Offer Credential",
          example = IssueCredentialOfferInvitation.Example
        )

    object metaRetries
        extends Annotation[Int](
          description = "The maximum background processing attempts remaining for this record.",
          example = 5
        )

    object metaLastFailure
        extends Annotation[ErrorResponse](
          description = "The last failure if any.",
          example = ErrorResponse.example
        )

  }

  given encoder: JsonEncoder[IssueCredentialRecord] =
    DeriveJsonEncoder.gen[IssueCredentialRecord]

  given decoder: JsonDecoder[IssueCredentialRecord] =
    DeriveJsonDecoder.gen[IssueCredentialRecord]

  given schema: Schema[IssueCredentialRecord] = Schema.derived

}
