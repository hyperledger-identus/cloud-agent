package org.hyperledger.identus.server.notification

import org.hyperledger.identus.connections.controller.http.Connection
import org.hyperledger.identus.connections.core.model.ConnectionRecord
import org.hyperledger.identus.credentials.core.model.{
  IssueCredentialRecord as PolluxIssueCredentialRecord,
  PresentationRecord as PolluxPresentationRecord
}
import org.hyperledger.identus.did.controller.http.ManagedDID
import org.hyperledger.identus.did.controller.http.ManagedDID.*
import org.hyperledger.identus.issue.controller.http.IssueCredentialRecord
import org.hyperledger.identus.notifications.Event
import org.hyperledger.identus.presentproof.controller.http.PresentationStatus
import org.hyperledger.identus.shared.models.WalletId
import org.hyperledger.identus.wallet.model.ManagedDIDDetail
import zio.*
import zio.json.*

import java.util.UUID

object JsonEventEncoders {

  implicit val connectionRecordEncoder: JsonEncoder[ConnectionRecord] =
    Connection.encoder.contramap(implicitly[Conversion[ConnectionRecord, Connection]].convert)

  implicit val issueCredentialRecordEncoder: JsonEncoder[PolluxIssueCredentialRecord] =
    IssueCredentialRecord.encoder.contramap(
      implicitly[Conversion[PolluxIssueCredentialRecord, IssueCredentialRecord]].convert
    )

  implicit val presentationRecordEncoder: JsonEncoder[PolluxPresentationRecord] =
    PresentationStatus.encoder.contramap(implicitly[Conversion[PolluxPresentationRecord, PresentationStatus]].convert)

  implicit val managedDIDDetailEncoder: JsonEncoder[ManagedDIDDetail] =
    ManagedDID.encoder.contramap(implicitly[Conversion[ManagedDIDDetail, ManagedDID]].convert)

  implicit val walletIdEncoder: JsonEncoder[WalletId] = summon[JsonEncoder[UUID]].contramap(_.toUUID)

  implicit def eventEncoder[T](implicit jsonEncoder: JsonEncoder[T]): JsonEncoder[Event[T]] =
    DeriveJsonEncoder.gen[Event[T]]
}
