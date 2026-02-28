package org.hyperledger.identus.credentials.sql.model

private[sql] final case class JWTCredentialRow(
    batchId: String,
    credentialId: String,
    content: String
)
