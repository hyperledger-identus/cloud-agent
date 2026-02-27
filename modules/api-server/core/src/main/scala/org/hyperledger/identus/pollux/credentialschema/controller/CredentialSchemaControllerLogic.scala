package org.hyperledger.identus.pollux.credentialschema.controller

import org.hyperledger.identus.api.http.model.{CollectionStats, Pagination}
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.pollux.credentialschema.http.{
  CredentialSchemaDidUrlResponsePage,
  CredentialSchemaResponsePage
}
import sttp.model.Uri

case class CredentialSchemaControllerLogic(
    ctx: RequestContext,
    pagination: Pagination,
    stats: CollectionStats
) {

  val self = ctx.request.uri.toString
  val pageOf = ctx.request.uri.copy(querySegments = Seq.empty).toString

  def result(page: CredentialSchemaResponsePage): CredentialSchemaResponsePage = {
    val next = PaginationUtils.composeNextUri(ctx.request.uri, page.contents, pagination, stats).map(_.toString)
    val previous = PaginationUtils.composePreviousUri(ctx.request.uri, page.contents, pagination, stats).map(_.toString)

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

  def resultDidUrl(page: CredentialSchemaDidUrlResponsePage): CredentialSchemaDidUrlResponsePage = {
    val next = PaginationUtils.composeNextUri(ctx.request.uri, page.contents, pagination, stats).map(_.toString)
    val previous = PaginationUtils.composePreviousUri(ctx.request.uri, page.contents, pagination, stats).map(_.toString)

    val pageResult = page.copy(
      self = self,
      pageOf = pageOf,
      next = next,
      previous = previous,
      contents = page.contents
    )

    pageResult
  }
}
