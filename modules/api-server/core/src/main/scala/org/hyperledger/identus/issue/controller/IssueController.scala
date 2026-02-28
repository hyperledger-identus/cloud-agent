package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.issue.controller.http.{
  AcceptCredentialOfferInvitation,
  AcceptCredentialOfferRequest,
  CreateIssueCredentialRecordRequest,
  IssueCredentialRecord,
  IssueCredentialRecordPage
}
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

trait IssueController {
  def createCredentialOffer(request: CreateIssueCredentialRecordRequest)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

  def createCredentialOfferInvitation(request: CreateIssueCredentialRecordRequest)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

  def acceptCredentialOfferInvitation(
      request: AcceptCredentialOfferInvitation
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

  def getCredentialRecords(paginationInput: PaginationInput, thid: Option[String])(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecordPage]

  def getCredentialRecord(recordId: String)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

  def acceptCredentialOffer(recordId: String, request: AcceptCredentialOfferRequest)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

  def issueCredential(recordId: String)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord]

}
