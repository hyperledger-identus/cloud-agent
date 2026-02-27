package org.hyperledger.identus.pollux.credentialschema.controller

import org.hyperledger.identus.api.http.model.{CollectionStats, Pagination}
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.pollux.credentialschema.http.{VerificationPolicyResponse, VerificationPolicyResponsePage}
import sttp.model.Uri

case class VerificationPolicyPageRequestLogic(
    ctx: RequestContext,
    pagination: Pagination,
    items: List[VerificationPolicyResponse],
    stats: CollectionStats
) {
  def composeNextUri(uri: Uri): Option[Uri] = PaginationUtils.composeNextUri(uri, items, pagination, stats)

  def composePreviousUri(uri: Uri): Option[Uri] = PaginationUtils.composePreviousUri(uri, items, pagination, stats)

  def result: VerificationPolicyResponsePage = {
    val self = ctx.request.uri.toString
    val pageOf = ctx.request.uri.copy(querySegments = Seq.empty).toString
    val next = composeNextUri(ctx.request.uri).map(_.toString)
    val previous = composePreviousUri(ctx.request.uri).map(_.toString)
    val baseUri = ctx.request.uri.copy(querySegments = Seq.empty)

    val pageResult = VerificationPolicyResponsePage(
      self = self,
      kind = "VerificationPolicyPage",
      pageOf = pageOf,
      next = next,
      previous = previous,
      contents = items.map(item => item.withBaseUri(baseUri))
    )

    pageResult
  }
}
