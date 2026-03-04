package org.hyperledger.identus.credentials.core.protocol

import org.hyperledger.identus.credentials.core.model.{CredentialFormat, DidCommID}
import org.hyperledger.identus.credentials.core.service.CredentialService
import org.hyperledger.identus.shared.models.{Failure, WalletAccessContext}
import org.hyperledger.identus.shared.protocols.*
import zio.*
import zio.json.ast.Json


/** Strangler fig adapter: bridges the IssuanceProtocol contract to the existing CredentialService.
  *
  * This adapter allows new code to use the IssuanceProtocol contract while the underlying
  * implementation still delegates to CredentialService. As CredentialService is decomposed,
  * this adapter can be replaced with a direct implementation.
  */
class DIDCommIssuanceAdapter(
    credentialService: CredentialService,
    walletCtx: WalletAccessContext,
) extends IssuanceProtocol:

  override def protocolId: ProtocolId = ProtocolId("aries-issue-v3")
  override def transport: TransportType = TransportType.DIDComm

  override def initiateOffer(params: Json): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "initiateOffer requires format-specific parameters; use CredentialService directly during migration"
    ))

  override def processOffer(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "processOffer requires DIDComm message parsing; use CredentialService.receiveCredentialOffer during migration"
    ))

  override def createRequest(recordId: RecordId): IO[Throwable, RecordId] =
    val didCommId = DidCommID(recordId.value.toString)
    credentialService.getById(didCommId).provide(ZLayer.succeed(walletCtx)).flatMap { record =>
      val effect = record.credentialFormat match
        case CredentialFormat.JWT =>
          credentialService.generateJWTCredentialRequest(didCommId)
        case CredentialFormat.SDJWT =>
          credentialService.generateSDJWTCredentialRequest(didCommId)
        case CredentialFormat.AnonCreds =>
          credentialService.generateAnonCredsCredentialRequest(didCommId)
      effect.provide(ZLayer.succeed(walletCtx))
        .mapBoth(e => new Exception(s"createRequest failed: $e"), r => RecordId(r.id.uuid))
    }.mapError(e => new Exception(s"createRequest failed: $e"))

  override def processRequest(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "processRequest requires DIDComm message parsing; use CredentialService.receiveCredentialRequest during migration"
    ))

  override def issueCredential(recordId: RecordId): IO[Throwable, RecordId] =
    val didCommId = DidCommID(recordId.value.toString)
    credentialService.getById(didCommId).provide(ZLayer.succeed(walletCtx)).flatMap { record =>
      val effect = record.credentialFormat match
        case CredentialFormat.JWT =>
          credentialService.generateJWTCredential(didCommId, "")
        case CredentialFormat.SDJWT =>
          credentialService.generateSDJWTCredential(didCommId, Duration.fromSeconds(365 * 24 * 3600L))
        case CredentialFormat.AnonCreds =>
          credentialService.generateAnonCredsCredential(didCommId)
      effect.provide(ZLayer.succeed(walletCtx))
        .mapBoth(e => new Exception(s"issueCredential failed: $e"), r => RecordId(r.id.uuid))
    }.mapError(e => new Exception(s"issueCredential failed: $e"))

  override def processCredential(message: ProtocolMessage): IO[Throwable, RecordId] =
    ZIO.fail(new UnsupportedOperationException(
      "processCredential requires DIDComm message parsing; use CredentialService.receiveCredentialIssue during migration"
    ))

  override def markSent(recordId: RecordId, phase: Phase): IO[Throwable, Unit] =
    val didCommId = DidCommID(recordId.value.toString)
    val effect = phase match
      case Phase.Offer => credentialService.markOfferSent(didCommId)
      case Phase.Request => credentialService.markRequestSent(didCommId)
      case Phase.Credential => credentialService.markCredentialSent(didCommId)
      case _ => ZIO.fail(new IllegalArgumentException(s"Unsupported phase for markSent: $phase"))
    effect.provide(ZLayer.succeed(walletCtx))
      .mapBoth(e => new Exception(s"markSent failed: $e"), _ => ())

  override def reportFailure(recordId: RecordId, reason: Failure): IO[Throwable, Unit] =
    val didCommId = DidCommID(recordId.value.toString)
    credentialService.reportProcessingFailure(didCommId, Some(reason))
      .provide(ZLayer.succeed(walletCtx))
