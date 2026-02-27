package org.hyperledger.identus.pollux.core.model

import java.util.UUID

// type DidCommID = String
opaque type DidCommID = String
object DidCommID:
  def apply(value: String): DidCommID = value
  def apply(): DidCommID = UUID.randomUUID.toString()
  extension (id: DidCommID)
    def value: String = id
    def uuid: UUID = UUID.fromString(id)
