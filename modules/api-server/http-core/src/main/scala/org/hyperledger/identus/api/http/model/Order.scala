package org.hyperledger.identus.api.http.model

import scala.annotation.unused

case class Order(field: String, direction: Option[Order.Direction] = None)

object Order {
  val DefaultDirection: Direction = Direction.Ascending
  val empty: Order = Order("")

  enum Direction(@unused kind: String):
    case Ascending extends Direction("asc")
    case Descending extends Direction("desc")
}
