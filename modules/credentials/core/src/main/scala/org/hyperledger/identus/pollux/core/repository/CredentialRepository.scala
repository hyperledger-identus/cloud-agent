package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.mercury.protocol.issuecredential.{IssueCredential, RequestCredential}
import org.hyperledger.identus.pollux.anoncreds.AnoncredCredentialRequestMetadata
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.ProtocolState
import org.hyperledger.identus.shared.models.*
import zio.*

trait CredentialRepository {
  def create(
      record: IssueCredentialRecord
  ): URIO[WalletAccessContext, Unit]

  def findAll(
      ignoreWithZeroRetries: Boolean,
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): URIO[WalletAccessContext, (Seq[IssueCredentialRecord], RuntimeFlags)]

  def getById(
      recordId: DidCommID
  ): URIO[WalletAccessContext, IssueCredentialRecord]

  def findById(
      recordId: DidCommID
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]]

  def findByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): URIO[WalletAccessContext, Seq[IssueCredentialRecord]]

  def findByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): UIO[Seq[IssueCredentialRecord]]

  def findByThreadId(
      thid: DidCommID,
      ignoreWithZeroRetries: Boolean,
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]]

  def updateProtocolState(
      recordId: DidCommID,
      from: IssueCredentialRecord.ProtocolState,
      to: IssueCredentialRecord.ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def updateWithSubjectId(
      recordId: DidCommID,
      subjectId: String,
      keyId: Option[KeyId],
      protocolState: ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def updateWithJWTRequestCredential(
      recordId: DidCommID,
      request: RequestCredential,
      protocolState: ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def updateWithAnonCredsRequestCredential(
      recordId: DidCommID,
      request: RequestCredential,
      metadata: AnoncredCredentialRequestMetadata,
      protocolState: ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def updateWithIssueCredential(
      recordId: DidCommID,
      issue: IssueCredential,
      protocolState: ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def updateWithIssuedRawCredential(
      recordId: DidCommID,
      issue: IssueCredential,
      issuedRawCredential: String,
      schemaUris: Option[List[String]],
      credentialDefinitionUri: Option[String],
      protocolState: ProtocolState
  ): URIO[WalletAccessContext, Unit]

  def deleteById(
      recordId: DidCommID
  ): URIO[WalletAccessContext, Unit]

  def findValidIssuedCredentials(
      recordId: Seq[DidCommID]
  ): URIO[WalletAccessContext, Seq[ValidIssuedCredentialRecord]]

  def findValidAnonCredsIssuedCredentials(
      recordIds: Seq[DidCommID]
  ): URIO[WalletAccessContext, Seq[ValidFullIssuedCredentialRecord]]

  def updateAfterFail(
      recordId: DidCommID,
      failReason: Option[Failure]
  ): URIO[WalletAccessContext, Unit]

}
