package org.hyperledger.identus.shared.models

import zio.json.*

opaque type KeyId = String
object KeyId:
  def apply(value: String): KeyId = value
  extension (id: KeyId) def value: String = id
  given decoder: JsonDecoder[KeyId] = JsonDecoder.string.map(KeyId(_))
  given encoder: JsonEncoder[KeyId] = JsonEncoder.string.contramap[KeyId](_.value)
