package org.hyperledger.identus.pollux.vc.jwt

case class CredentialSchemaAndTrustedIssuersConstraint(
    schemaId: String,
    trustedIssuers: Option[Seq[String]]
)
