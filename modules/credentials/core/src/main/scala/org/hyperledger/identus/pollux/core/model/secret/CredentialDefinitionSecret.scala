package org.hyperledger.identus.pollux.core.model.secret

import org.hyperledger.identus.agent.walletapi.storage.GenericSecret
import zio.json.ast.Json

import java.util.UUID
import scala.util.Try

final case class CredentialDefinitionSecret(json: Json)

object CredentialDefinitionSecret {
  given GenericSecret[UUID, CredentialDefinitionSecret] = new {
    override def keyPath(id: UUID): String = s"credential-definitions/${id.toString}"

    override def encodeValue(secret: CredentialDefinitionSecret): Json = secret.json

    override def decodeValue(json: Json): Try[CredentialDefinitionSecret] = Try(CredentialDefinitionSecret(json))
  }
}
