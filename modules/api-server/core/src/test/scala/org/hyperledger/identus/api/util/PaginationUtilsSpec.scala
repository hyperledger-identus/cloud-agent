package org.hyperledger.identus.api.util

import org.hyperledger.identus.api.http.model.{CollectionStats, Pagination}
import sttp.model.Uri
import zio.*
import zio.test.*
import zio.test.Assertion.*

object PaginationUtilsSpec extends ZIOSpecDefault {

  override def spec = suite("PaginationUtils")(composeNexUriSpec, composePreviousUriSpec)

  private val composeNexUriSpec = suite("composeNextUri")(
    test("return next URI when more items are avialble") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = (20 to 30).toSeq
      val pagination = Pagination(offset = 20, limit = 10)
      val stats = CollectionStats(totalCount = 50, filteredCount = 50)
      val nextUri = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString)
      assert(nextUri)(isSome(equalTo("http://example.com?foo=bar&offset=30&limit=10")))
    },
    test("not return next URI when on last page") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = (40 to 50).toSeq
      val pagination = Pagination(offset = 40, limit = 10)
      val stats = CollectionStats(totalCount = 50, filteredCount = 50)
      val nextUri = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString)
      assert(nextUri)(isNone)
    },
    test("not return next URI when item count is less than limit") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = (45 to 50).toSeq
      val pagination = Pagination(offset = 45, limit = 10)
      val stats = CollectionStats(totalCount = 50, filteredCount = 50)
      val nextUri = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString)
      assert(nextUri)(isNone)
    },
    test("not return next URI when result is empty") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = Nil
      val pagination = Pagination(offset = 20, limit = 10)
      val stats = CollectionStats(totalCount = 0, filteredCount = 0)
      val nextUri = PaginationUtils.composeNextUri(uri, items, pagination, stats).map(_.toString)
      assert(nextUri)(isNone)
    }
  )

  private val composePreviousUriSpec = suite("composePreviousUri")(
    test("return prev URI when more items are avaiable") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = (20 to 30).toSeq
      val pagination = Pagination(offset = 20, limit = 10)
      val stats = CollectionStats(totalCount = 50, filteredCount = 50)
      val prevUri = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString)
      assert(prevUri)(isSome(equalTo("http://example.com?foo=bar&offset=10&limit=10")))
    },
    test("not return prev URI when on first page") {
      val uri = Uri.parse("http://example.com?foo=bar").toOption.get
      val items = (0 to 10).toSeq
      val pagination = Pagination(offset = 0, limit = 10)
      val stats = CollectionStats(totalCount = 50, filteredCount = 50)
      val prevUri = PaginationUtils.composePreviousUri(uri, items, pagination, stats).map(_.toString)
      assert(prevUri)(isNone)
    }
  )

}
