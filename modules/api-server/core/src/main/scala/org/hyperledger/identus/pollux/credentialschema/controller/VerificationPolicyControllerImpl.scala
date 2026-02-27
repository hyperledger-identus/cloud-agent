package org.hyperledger.identus.pollux.credentialschema.controller

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.model.{CollectionStats, Order, Pagination}
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.CredentialSchemaAndTrustedIssuersConstraint
import org.hyperledger.identus.pollux.core.service.VerificationPolicyService
import org.hyperledger.identus.pollux.credentialschema.http.{
  VerificationPolicyInput,
  VerificationPolicyResponse,
  VerificationPolicyResponsePage
}
import org.hyperledger.identus.pollux.credentialschema.http.VerificationPolicyResponse.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.ZIO.*

import java.util.UUID
import scala.language.implicitConversions

class VerificationPolicyControllerImpl(service: VerificationPolicyService) extends VerificationPolicyController {

  override def createVerificationPolicy(
      ctx: RequestContext,
      in: VerificationPolicyInput
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse] = {
    val constraints = in.constraints
      .map(c =>
        CredentialSchemaAndTrustedIssuersConstraint(
          c.schemaId,
          c.trustedIssuers
        )
      )

    for {
      createdVerificationPolicy <- service.create(
        in.name,
        in.description,
        constraints
      )
    } yield createdVerificationPolicy
      .toSchema()
      .withBaseUri(ctx.request.uri)
  }

  override def getVerificationPolicyById(
      ctx: RequestContext,
      id: UUID
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse] =
    service.get(id).map(_.toSchema().withUri(ctx.request.uri))

  override def updateVerificationPolicyById(
      ctx: RequestContext,
      id: UUID,
      nonce: Int,
      update: VerificationPolicyInput
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponse] =
    for {
      constraints <- zio.ZIO.succeed(
        update.constraints.toVector // TODO: refactor to Seq
          .map(c =>
            CredentialSchemaAndTrustedIssuersConstraint(
              c.schemaId,
              c.trustedIssuers
            )
          )
      )
      vp <- model.VerificationPolicy.make(
        update.name,
        update.description,
        constraints,
        nonce = nonce + 1
      )
      updated <- service.update(id, nonce, vp)
    } yield updated.toSchema().withUri(ctx.request.uri)

  override def deleteVerificationPolicyById(
      ctx: RequestContext,
      id: UUID
  ): ZIO[WalletAccessContext, ErrorResponse, Unit] =
    service
      .delete(id)
      .as(())

  override def lookupVerificationPolicies(
      ctx: RequestContext,
      filter: VerificationPolicyResponse.Filter,
      pagination: Pagination,
      order: Option[Order]
  ): ZIO[WalletAccessContext, ErrorResponse, VerificationPolicyResponsePage] = {
    for {
      filteredDomainRecords <- service
        .lookup(filter.name, Some(pagination.offset), Some(pagination.limit))
      filteredCount <- service
        .filteredCount(filter.name)
      baseUri = ctx.request.uri.copy(querySegments = Seq.empty)
      filteredRecords = filteredDomainRecords.map(
        _.toSchema().withBaseUri(baseUri)
      )
      totalCount <- service
        .totalCount()
      response = VerificationPolicyPageRequestLogic(
        ctx,
        pagination,
        filteredRecords,
        CollectionStats(totalCount, filteredCount)
      ).result
    } yield response
  }
}

object VerificationPolicyControllerImpl {
  val layer: URLayer[VerificationPolicyService, VerificationPolicyController] =
    ZLayer.fromFunction(VerificationPolicyControllerImpl(_))
}
