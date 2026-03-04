package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.json.ast.Json

/** Strangler fig adapter: bridges the PresentationProtocol contract to OID4VP.
  *
  * OID4VP uses redirect-based verification (not message-passing like DIDComm).
  * Most methods are unsupported — the adapter formalizes OID4VP as a protocol
  * within the module registry for discovery and capability resolution.
  */
class OidcPresentationAdapter extends PresentationProtocol:

  override def protocolId: ProtocolId = ProtocolId("oid4vp")
  override def transport: TransportType = TransportType.OIDC

  override def requestPresentation(params: Json): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VP uses redirect-based presentation requests, not message-based"
    ))

  override def processRequest(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VP processes requests via HTTP authorization endpoint"
    ))

  override def createPresentation(recordId: RecordId): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VP presentation creation is handled by the wallet via HTTP"
    ))

  override def processPresentation(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VP presentation delivery uses HTTP redirect, not messages"
    ))

  override def verifyPresentation(recordId: RecordId): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VP verification is handled by the verifier's HTTP endpoint"
    ))
