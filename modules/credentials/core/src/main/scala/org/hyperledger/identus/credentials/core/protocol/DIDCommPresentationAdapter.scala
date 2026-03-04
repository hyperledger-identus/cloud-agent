package org.hyperledger.identus.credentials.core.protocol

import org.hyperledger.identus.credentials.core.model.{CredentialFormat, DidCommID}
import org.hyperledger.identus.credentials.core.service.PresentationService
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.json.ast.Json


/** Strangler fig adapter: bridges the PresentationProtocol contract to the existing PresentationService.
  *
  * This adapter allows new code to use the PresentationProtocol contract while the underlying
  * implementation still delegates to PresentationService. As PresentationService is decomposed,
  * this adapter can be replaced with a direct implementation.
  */
class DIDCommPresentationAdapter(
    presentationService: PresentationService,
    walletCtx: WalletAccessContext,
) extends PresentationProtocol:

  override def protocolId: ProtocolId = ProtocolId("aries-present-v3")
  override def transport: TransportType = TransportType.DIDComm

  override def requestPresentation(params: Json): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "requestPresentation requires format-specific parameters; use PresentationService directly during migration"
    ))

  override def processRequest(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "processRequest requires DIDComm message parsing; use PresentationService.receiveRequestPresentation during migration"
    ))

  override def createPresentation(recordId: RecordId): IO[Throwable, RecordId] =
    val didCommId = DidCommID(recordId.value.toString)
    presentationService.findPresentationRecord(didCommId)
      .provide(ZLayer.succeed(walletCtx))
      .flatMap {
        case None => ZIO.fail(new NoSuchElementException(s"Presentation record not found: $recordId"))
        case Some(record) =>
          val effect = record.credentialFormat match
            case CredentialFormat.JWT =>
              presentationService.acceptRequestPresentation(didCommId, Seq.empty)
            case CredentialFormat.SDJWT =>
              presentationService.acceptSDJWTRequestPresentation(didCommId, Seq.empty, None)
            case CredentialFormat.AnonCreds =>
              ZIO.fail(new UnsupportedOperationException(
                "AnonCreds presentation requires credential proofs; use PresentationService directly"
              ))
          effect.provide(ZLayer.succeed(walletCtx))
            .mapBoth(e => new Exception(s"createPresentation failed: $e"), r => RecordId(r.id.uuid))
      }.mapError(e => new Exception(s"createPresentation failed: $e"))

  override def processPresentation(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "processPresentation requires DIDComm message parsing; use PresentationService.receivePresentation during migration"
    ))

  override def verifyPresentation(recordId: RecordId): IO[Throwable, RecordId] =
    val didCommId = DidCommID(recordId.value.toString)
    presentationService.acceptPresentation(didCommId)
      .provide(ZLayer.succeed(walletCtx))
      .mapBoth(e => new Exception(s"verifyPresentation failed: $e"), r => RecordId(r.id.uuid))
