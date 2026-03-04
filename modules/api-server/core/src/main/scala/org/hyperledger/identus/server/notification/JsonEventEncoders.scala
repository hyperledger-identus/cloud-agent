package org.hyperledger.identus.server.notification

import org.hyperledger.identus.connections.core.model.ConnectionRecord
import org.hyperledger.identus.connections.core.model.ConnectionRecord.Role
import org.hyperledger.identus.credentials.core.model.{
  IssueCredentialRecord as PolluxIssueCredentialRecord,
  PresentationRecord as PolluxPresentationRecord
}
import org.hyperledger.identus.did.core.model.did.PrismDID
import org.hyperledger.identus.didcomm.model.{AttachmentDescriptor, Base64, JsonData}
import org.hyperledger.identus.didcomm.protocol.invitation.v2.Invitation
import org.hyperledger.identus.didcomm.protocol.presentproof.{Presentation, RequestPresentation}
import org.hyperledger.identus.shared.models.Failure
import org.hyperledger.identus.wallet.model.{ManagedDIDDetail, PublicationState}
import zio.json.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.matching.Regex

object JsonEventEncoders {

  // ---------------------------------------------------------------------------
  // Webhook-specific DTOs (private, matching JSON field names of the REST API)
  // ---------------------------------------------------------------------------

  private case class WebhookInvitation(
      id: UUID,
      `type`: String,
      from: String,
      invitationUrl: String
  )
  private given JsonEncoder[WebhookInvitation] = DeriveJsonEncoder.gen

  private case class WebhookErrorResponse(
      status: Int,
      `type`: String,
      title: String,
      detail: Option[String] = None,
      instance: String = s"error:instance:${UUID.randomUUID()}"
  )
  private given JsonEncoder[WebhookErrorResponse] = DeriveJsonEncoder.gen

  private case class WebhookConnection(
      connectionId: UUID,
      thid: String,
      label: Option[String] = None,
      goalCode: Option[String] = None,
      goal: Option[String] = None,
      myDid: Option[String] = None,
      theirDid: Option[String] = None,
      role: String,
      state: String,
      invitation: WebhookInvitation,
      createdAt: OffsetDateTime,
      updatedAt: Option[OffsetDateTime] = None,
      metaRetries: Int,
      metaLastFailure: Option[WebhookErrorResponse] = None,
      self: String = "",
      kind: String = "Connection"
  )
  private given JsonEncoder[WebhookConnection] = DeriveJsonEncoder.gen

  private case class WebhookManagedDID(
      did: String,
      longFormDid: Option[String] = None,
      status: String
  )
  private given JsonEncoder[WebhookManagedDID] = DeriveJsonEncoder.gen

  private case class WebhookIssueCredentialOfferInvitation(
      id: UUID,
      `type`: String,
      from: String,
      invitationUrl: String
  )
  private given JsonEncoder[WebhookIssueCredentialOfferInvitation] = DeriveJsonEncoder.gen

  private case class WebhookIssueCredentialRecord(
      recordId: String,
      thid: String,
      credentialFormat: String,
      subjectId: Option[String] = None,
      validityPeriod: Option[Double] = None,
      claims: zio.json.ast.Json,
      automaticIssuance: Option[Boolean] = None,
      createdAt: OffsetDateTime,
      updatedAt: Option[OffsetDateTime] = None,
      role: String,
      protocolState: String,
      credential: Option[String] = None,
      issuingDID: Option[String] = None,
      goalCode: Option[String] = None,
      goal: Option[String] = None,
      myDid: Option[String] = None,
      invitation: Option[WebhookIssueCredentialOfferInvitation] = None,
      metaRetries: Int,
      metaLastFailure: Option[WebhookErrorResponse] = None
  )
  private given JsonEncoder[WebhookIssueCredentialRecord] = DeriveJsonEncoder.gen

  private case class WebhookProofRequestAux(
      schemaId: String,
      trustIssuers: Seq[String]
  )
  private given JsonEncoder[WebhookProofRequestAux] = DeriveJsonEncoder.gen

  private case class WebhookOOBPresentationInvitation(
      id: UUID,
      `type`: String,
      from: String,
      invitationUrl: String
  )
  private given JsonEncoder[WebhookOOBPresentationInvitation] = DeriveJsonEncoder.gen

  private case class WebhookPresentationStatus(
      presentationId: String,
      thid: String,
      role: String,
      status: String,
      proofs: Seq[WebhookProofRequestAux],
      data: Seq[String],
      requestData: Seq[String],
      disclosedClaims: Option[zio.json.ast.Json],
      connectionId: Option[String] = None,
      goalCode: Option[String] = None,
      goal: Option[String] = None,
      myDid: Option[String] = None,
      invitation: Option[WebhookOOBPresentationInvitation] = None,
      metaRetries: Int,
      metaLastFailure: Option[WebhookErrorResponse] = None
  )
  private given JsonEncoder[WebhookPresentationStatus] = DeriveJsonEncoder.gen

  // ---------------------------------------------------------------------------
  // Shared helpers
  // ---------------------------------------------------------------------------

  private val CamelCaseSplitRegex: Regex = "(([A-Z]?[a-z]+)|([A-Z]))".r

  private def failureToWebhookError(failure: Failure): WebhookErrorResponse = {
    val simpleName = failure.getClass.getSimpleName
    WebhookErrorResponse(
      failure.statusCode.code,
      s"error:${failure.namespace}:$simpleName",
      CamelCaseSplitRegex.findAllIn(simpleName).mkString(" "),
      Some(failure.userFacingMessage)
    )
  }

  private def invitationFromDomain(invitation: Invitation): WebhookInvitation =
    WebhookInvitation(
      id = UUID.fromString(invitation.id),
      `type` = invitation.`type`,
      from = invitation.from.value,
      invitationUrl = s"https://my.domain.com/path?_oob=${invitation.toBase64}"
    )

  private def extractData[A](
      maybePresentation: Option[A],
      extractAttachments: A => Seq[AttachmentDescriptor]
  ): Seq[String] =
    maybePresentation match
      case Some(p) =>
        extractAttachments(p).head.data match {
          case Base64(data) =>
            val base64Decoded = new String(java.util.Base64.getUrlDecoder.decode(data))
            Seq(base64Decoded)
          case JsonData(jsonData) =>
            Seq(jsonData.toJson)
          case _ => FeatureNotImplemented
        }
      case None => Seq.empty

  // ---------------------------------------------------------------------------
  // Domain -> Webhook DTO conversions
  // ---------------------------------------------------------------------------

  private def toWebhookConnection(domain: ConnectionRecord): WebhookConnection =
    WebhookConnection(
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
      invitation = invitationFromDomain(domain.invitation),
      createdAt = domain.createdAt.atOffset(ZoneOffset.UTC),
      updatedAt = domain.updatedAt.map(_.atOffset(ZoneOffset.UTC)),
      metaRetries = domain.metaRetries,
      metaLastFailure = domain.metaLastFailure.map(failureToWebhookError),
      self = domain.id.toString,
      kind = "Connection",
    )

  private def toWebhookManagedDID(didDetail: ManagedDIDDetail): WebhookManagedDID = {
    val operation = didDetail.state.createOperation
    val (longFormDID, status) = didDetail.state.publicationState match {
      case PublicationState.Created() =>
        Some(PrismDID.buildLongFormFromOperation(operation)) -> "CREATED"
      case PublicationState.PublicationPending(_) =>
        Some(PrismDID.buildLongFormFromOperation(operation)) -> "PUBLICATION_PENDING"
      case PublicationState.Published(_) =>
        None -> "PUBLISHED"
    }
    WebhookManagedDID(
      did = didDetail.did.toString,
      longFormDid = longFormDID.map(_.toString),
      status = status
    )
  }

  private def toWebhookIssueCredentialRecord(domain: PolluxIssueCredentialRecord): WebhookIssueCredentialRecord =
    WebhookIssueCredentialRecord(
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
      invitation = domain.invitation.map(invitation =>
        WebhookIssueCredentialOfferInvitation(
          id = UUID.fromString(invitation.id),
          `type` = invitation.`type`,
          from = invitation.from.value,
          invitationUrl = s"https://my.domain.com/path?_oob=${invitation.toBase64}"
        )
      ),
      goalCode = domain.invitation.flatMap(_.body.goal_code),
      goal = domain.invitation.flatMap(_.body.goal),
      myDid = domain.invitation.map(_.from.value),
      metaRetries = domain.metaRetries,
      metaLastFailure = domain.metaLastFailure.map(failureToWebhookError),
    )

  private def toWebhookPresentationStatus(domain: PolluxPresentationRecord): WebhookPresentationStatus = {
    val data = extractData(domain.presentationData, (p: Presentation) => p.attachments)
    val requestData = extractData(domain.requestPresentationData, (p: RequestPresentation) => p.attachments)
    WebhookPresentationStatus(
      domain.id.value,
      thid = domain.thid.value,
      role = domain.role.toString,
      status = domain.protocolState.toString,
      proofs = Seq.empty,
      data = data,
      disclosedClaims = domain.sdJwtDisclosedClaims,
      requestData = requestData,
      connectionId = domain.connectionId,
      invitation = domain.invitation.map(invitation =>
        WebhookOOBPresentationInvitation(
          id = UUID.fromString(invitation.id),
          `type` = invitation.`type`,
          from = invitation.from.value,
          invitationUrl = s"https://my.domain.com/path?_oob=${invitation.toBase64}"
        )
      ),
      goalCode = domain.invitation.flatMap(_.body.goal_code),
      goal = domain.invitation.flatMap(_.body.goal),
      myDid = domain.invitation.map(_.from.value),
      metaRetries = domain.metaRetries,
      metaLastFailure = domain.metaLastFailure.map(failureToWebhookError),
    )
  }

  // ---------------------------------------------------------------------------
  // Public encode functions (domain record -> Json)
  // ---------------------------------------------------------------------------

  def encodeConnectionRecord(record: ConnectionRecord): Json =
    toWebhookConnection(record).toJsonAST.toOption.get

  def encodeIssueCredentialRecord(record: PolluxIssueCredentialRecord): Json =
    toWebhookIssueCredentialRecord(record).toJsonAST.toOption.get

  def encodePresentationRecord(record: PolluxPresentationRecord): Json =
    toWebhookPresentationStatus(record).toJsonAST.toOption.get

  def encodeManagedDIDDetail(detail: ManagedDIDDetail): Json =
    toWebhookManagedDID(detail).toJsonAST.toOption.get
}
