package org.hyperledger.identus.api.http.model

import sttp.tapir.EndpointIO.annotations.{description, query}
import sttp.tapir.Schema.annotations.validateEach
import sttp.tapir.Validator

case class PaginationInput(
    @query
    @validateEach(Validator.positiveOrZero[Int])
    @description("The number of items to skip before returning results. Default is 0 if not specified.")
    offset: Option[Int] = None,
    @query
    @validateEach(Validator.positive[Int])
    @description("The maximum number of items to return. Defaults to 100 if not specified.")
    limit: Option[Int] = None
) {
  def toPagination = Pagination.apply(this)
}

case class Pagination(offset: Int, limit: Int) {
  def next = copy(offset = offset + limit)
  def prev = copy(offset = Math.max(offset - limit, 0))
}

object Pagination {
  def apply(in: PaginationInput): Pagination =
    Pagination(in.offset.getOrElse(0), in.limit.getOrElse(100))
}
