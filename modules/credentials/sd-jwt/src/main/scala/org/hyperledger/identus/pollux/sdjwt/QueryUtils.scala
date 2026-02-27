package org.hyperledger.identus.pollux.sdjwt

import zio.json.*
import zio.json.ast.{Json, JsonCursor}
import zio.json.ast.Json.*

private[sdjwt] object QueryUtils {

  type AUX = Bool | Str | Num | Json.Null | None.type
  def getCursors(queryJson: Json, cursor: JsonCursor[?, ?]): Seq[(JsonCursor[?, ast.Json], AUX)] = {
    queryJson match
      case Obj(fields) if fields.isEmpty => Seq((cursor, None)) // especial case for SD-JDT lib
      case value: Bool                   => Seq((cursor, value))
      case value: Str                    => Seq((cursor, value))
      case value: Num                    => Seq((cursor, value))
      case Json.Null                     => Seq((cursor, Json.Null))
      case Arr(elements)                 =>
        elements.zipWithIndex.flatMap { case (json, index) =>
          val nextCursor = cursor.isArray.element(index)
          getCursors(json, nextCursor)
        }
      case Obj(fields) =>
        fields.flatMap { (k, v) =>
          val nextCursor = cursor.isObject.field(k)
          getCursors(v, nextCursor)
        }
  }

  def testClaims(query: Json, claims: Json) = {
    val expectedAux = getCursors(query, JsonCursor.identity)
    expectedAux.forall { case (cursor, value) =>
      value match
        case None      => claims.get(cursor).isRight // check is the path exists
        case Num(v)    => claims.get(cursor).map(_.asNumber.exists(_.value == v)).getOrElse(false)
        case Str(v)    => claims.get(cursor).map(_.asString.exists(_ == v)).getOrElse(false)
        case Bool(v)   => claims.get(cursor).map(_.asBoolean.exists(_ == v)).getOrElse(false)
        case Json.Null => claims.get(cursor).map(_.asNull.isDefined).getOrElse(false)
    }

  }
}
