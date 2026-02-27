package org.hyperledger.identus.pollux.core.model.oid4vci

import org.hyperledger.identus.pollux.core.model.CredentialFormat

import java.net.URI
import java.time.temporal.ChronoUnit
import java.time.Instant

final case class CredentialConfiguration(
    configurationId: String,
    format: CredentialFormat,
    schemaId: URI,
    createdAt: Instant
) {
  def scope: String = configurationId

  def withTruncatedTimestamp(unit: ChronoUnit = ChronoUnit.MICROS): CredentialConfiguration = copy(
    createdAt = createdAt.truncatedTo(unit),
  )
}
