package org.hyperledger.identus.shared.json

import zio.json.ast.Json

object JsonOps {
  extension (json: Json) {
    def removeNullValues: Json = json match
      case Json.Obj(fields) =>
        Json.Obj(fields.collect { case (key, value) if value != Json.Null => key -> value.removeNullValues })
      case Json.Arr(elements) =>
        Json.Arr(elements.map(_.removeNullValues))
      case other => other

    def removeField(name: String): Json = json match
      case Json.Obj(fields) =>
        Json.Obj(fields.filterNot { case (key, value) => key == name })
      case Json.Arr(elements) =>
        Json.Arr(elements.map(_.removeField(name)))
      case other => other
  }
}
