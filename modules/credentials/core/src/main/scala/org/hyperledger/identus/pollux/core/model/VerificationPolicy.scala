package org.hyperledger.identus.pollux.core.model

import zio.{Clock, Random}

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

case class VerificationPolicy(
    id: UUID,
    name: String,
    description: String,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
    constrains: Seq[VerificationPolicyConstraint],
    nonce: Int
)

object VerificationPolicy {
  def make(name: String, description: String, constraints: Seq[VerificationPolicyConstraint], nonce: Int = 0) =
    for {
      id <- Random.nextUUID
      ts <- Clock.currentDateTime.map(_.atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime)
    } yield VerificationPolicy(
      id,
      name,
      description,
      createdAt = ts,
      updatedAt = ts,
      constrains = constraints,
      nonce = nonce
    )
}
sealed trait VerificationPolicyConstraint

case class CredentialSchemaAndTrustedIssuersConstraint(
    schemaId: String,
    trustedIssuers: Seq[String]
) extends VerificationPolicyConstraint
