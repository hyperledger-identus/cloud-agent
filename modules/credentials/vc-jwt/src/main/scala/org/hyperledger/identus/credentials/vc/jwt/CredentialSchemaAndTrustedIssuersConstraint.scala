package org.hyperledger.identus.credentials.vc.jwt

case class CredentialSchemaAndTrustedIssuersConstraint(
    schemaId: String,
    trustedIssuers: Option[Seq[String]]
)
