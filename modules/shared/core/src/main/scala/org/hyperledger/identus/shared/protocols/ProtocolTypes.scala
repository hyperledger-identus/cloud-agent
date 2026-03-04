package org.hyperledger.identus.shared.protocols

import java.util.UUID

enum TransportType:
  case DIDComm, OIDC, KERI

/** Protocol identifier — includes version (e.g. "aries-issue-v2", "aries-issue-v3", "oid4vci") */
case class ProtocolId(value: String)

case class RecordId(value: UUID)

enum Phase:
  case Proposal, Offer, Request, Credential, Presentation, Verification

case class Endpoint(uri: String, metadata: Map[String, String] = Map.empty)

/** Transport-agnostic protocol message */
case class ProtocolMessage(
    id: String,
    `type`: String,
    body: String, // JSON string
    attachments: Seq[Array[Byte]] = Seq.empty,
)
