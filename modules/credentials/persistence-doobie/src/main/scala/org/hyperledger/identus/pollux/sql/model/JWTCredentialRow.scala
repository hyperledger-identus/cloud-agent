package org.hyperledger.identus.pollux.sql.model

private[sql] final case class JWTCredentialRow(
    batchId: String,
    credentialId: String,
    content: String
)
