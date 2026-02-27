package org.hyperledger.identus.oid4vci.domain

import org.hyperledger.identus.castor.core.model.did.{DID, PrismDID}

import java.util.UUID

case class IssuanceSession(
    id: UUID,
    issuerId: UUID,
    nonce: String,
    issuerState: String,
    schemaId: Option[String],
    claims: zio.json.ast.Json,
    subjectDid: Option[DID],
    issuingDid: PrismDID,
) {
  def withSchemaId(schemaId: String): IssuanceSession = copy(schemaId = Some(schemaId))
  def withSubjectDid(subjectDid: DID): IssuanceSession = copy(subjectDid = Some(subjectDid))
}
