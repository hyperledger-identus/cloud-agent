package org.hyperledger.identus.pollux.credentialschema.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{Order, Pagination}
import org.hyperledger.identus.pollux.credentialschema.http.{
  VerificationPolicyInput,
  VerificationPolicyResponse,
  VerificationPolicyResponsePage
}
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

trait VerificationPolicyController {
  def createVerificationPolicy(
      ctx: RequestContext,
      in: VerificationPolicyInput
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse]

  def getVerificationPolicyById(
      ctx: RequestContext,
      id: UUID
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse]

  def updateVerificationPolicyById(
      ctx: RequestContext,
      id: UUID,
      nonce: Int,
      update: VerificationPolicyInput
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse]

  def deleteVerificationPolicyById(
      ctx: RequestContext,
      id: UUID
  ): ZIO[WalletAccessContext, ErrorResponse, Unit]

  def lookupVerificationPolicies(
      ctx: RequestContext,
      filter: VerificationPolicyResponse.Filter,
      pagination: Pagination,
      order: Option[Order]
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponsePage]
}
