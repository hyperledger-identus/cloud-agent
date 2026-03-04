package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.oid4vci.storage.IssuanceSessionStorage
import org.hyperledger.identus.shared.models.Failure
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.json.ast.Json

/** Strangler fig adapter: bridges the IssuanceProtocol contract to the OID4VCI flow.
  *
  * OID4VCI uses a redirect-based flow (not message-passing like DIDComm), so many
  * IssuanceProtocol methods are not applicable. The adapter exposes what can be mapped
  * and fails explicitly on methods that require DIDComm semantics.
  */
class OidcIssuanceAdapter(
    sessionStorage: IssuanceSessionStorage,
) extends IssuanceProtocol:

  override def protocolId: ProtocolId = ProtocolId("oid4vci")
  override def transport: TransportType = TransportType.OIDC

  override def initiateOffer(params: Json): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "initiateOffer requires OIDCCredentialIssuerService.createCredentialOffer; use the HTTP layer directly"
    ))

  override def processOffer(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VCI uses redirect-based offer flow, not message-based"
    ))

  override def createRequest(recordId: RecordId): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VCI credential requests are initiated by the wallet via HTTP token endpoint"
    ))

  override def processRequest(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VCI processes requests via the credential endpoint HTTP handler"
    ))

  override def issueCredential(recordId: RecordId): IO[Throwable, RecordId] =
    sessionStorage.getByIssuerState(recordId.value.toString)
      .mapError(e => new Exception(s"issueCredential failed: ${e.message}"))
      .flatMap {
        case Some(session) => ZIO.succeed(RecordId(session.id))
        case None => ZIO.fail(new NoSuchElementException(s"No issuance session for state: ${recordId.value}"))
      }

  override def processCredential(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VCI credential delivery is handled via HTTP response, not message-based"
    ))

  override def markSent(recordId: RecordId, phase: Phase): IO[Throwable, Unit] =
    ZIO.fail(new UnsupportedOperationException(
      "OID4VCI does not use phase-based sent tracking; state is managed via IssuanceSession"
    ))

  override def reportFailure(recordId: RecordId, reason: Failure): IO[Throwable, Unit] =
    ZIO.logWarning(s"OID4VCI issuance failure for ${recordId.value}: ${reason.userFacingMessage}").unit
