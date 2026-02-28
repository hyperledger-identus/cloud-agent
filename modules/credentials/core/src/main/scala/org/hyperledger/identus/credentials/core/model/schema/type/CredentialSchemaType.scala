package org.hyperledger.identus.credentials.core.model.schema.`type`

import org.hyperledger.identus.credentials.core.model.schema.Schema
import org.hyperledger.identus.shared.json.JsonSchemaError
import zio.IO

trait CredentialSchemaType {
  val `type`: String

  def validate(schema: Schema): IO[JsonSchemaError, Unit]
}
