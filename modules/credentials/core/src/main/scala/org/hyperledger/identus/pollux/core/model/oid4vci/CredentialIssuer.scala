package org.hyperledger.identus.pollux.core.model.oid4vci

import java.net.URL
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.UUID

case class CredentialIssuer(
    id: UUID,
    authorizationServer: URL,
    authorizationServerClientId: String,
    authorizationServerClientSecret: String,
    createdAt: Instant,
    updatedAt: Instant
) {
  def withTruncatedTimestamp(unit: ChronoUnit = ChronoUnit.MICROS): CredentialIssuer = copy(
    createdAt = createdAt.truncatedTo(unit),
    updatedAt = updatedAt.truncatedTo(unit),
  )
}

object CredentialIssuer {
  def apply(authorizationServer: URL, clientId: String, clientSecret: String): CredentialIssuer =
    apply(UUID.randomUUID(), authorizationServer, clientId, clientSecret)

  def apply(id: UUID, authorizationServer: URL, clientId: String, clientSecret: String): CredentialIssuer = {
    val now = Instant.now
    CredentialIssuer(id, authorizationServer, clientId, clientSecret, now, now).withTruncatedTimestamp()
  }
}
