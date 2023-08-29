package io.iohk.atala.pollux.credentialdefinition.controller

import io.iohk.atala.api.http.RequestContext
import io.iohk.atala.api.http.model.CollectionStats
import io.iohk.atala.api.http.model.Pagination
import io.iohk.atala.api.util.PaginationUtils
import io.iohk.atala.pollux.credentialdefinition.http.CredentialDefinitionResponsePage
import sttp.model.Uri

case class CredentialDefinitionControllerLogic(
    ctx: RequestContext,
    pagination: Pagination,
    page: CredentialDefinitionResponsePage,
    stats: CollectionStats
) {

  def composeNextUri(uri: Uri): Option[Uri] =
    PaginationUtils.composeNextUri(uri, page.contents, pagination, stats)

  def composePreviousUri(uri: Uri): Option[Uri] =
    PaginationUtils.composePreviousUri(uri, page.contents, pagination, stats)

  def result: CredentialDefinitionResponsePage = {
    val self = ctx.request.uri.toString
    val pageOf = ctx.request.uri.copy(querySegments = Seq.empty).toString
    val next = composeNextUri(ctx.request.uri).map(_.toString)
    val previous = composePreviousUri(ctx.request.uri).map(_.toString)

    val pageResult = page.copy(
      self = self,
      pageOf = pageOf,
      next = next,
      previous = previous,
      contents = page.contents.map(item =>
        item.withBaseUri(
          ctx.request.uri.copy(querySegments = Seq.empty)
        )
      )
    )

    pageResult
  }
}
