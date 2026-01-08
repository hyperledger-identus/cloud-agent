package org.hyperledger.identus.presentproof.controller

import org.hyperledger.identus.agent.server.config.{AppConfig, FeatureFlagConfig}
import org.hyperledger.identus.agent.server.ControllerHelper
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.presentproof.{PresentCredentialRequestFormat, ProofType}
import org.hyperledger.identus.pollux.core.model.{CredentialFormat, DidCommID, PresentationRecord}
import org.hyperledger.identus.pollux.core.model.error.PresentationError
import org.hyperledger.identus.pollux.core.model.error.PresentationError.UnsupportedCredentialFormatBecauseDisabled
import org.hyperledger.identus.pollux.core.model.presentation.Options
import org.hyperledger.identus.pollux.core.model.CredentialFormat.{AnonCreds, JWT, SDJWT}
import org.hyperledger.identus.pollux.core.service.serdes.AnoncredPresentationRequestV1
import org.hyperledger.identus.pollux.core.service.PresentationService
import org.hyperledger.identus.presentproof.controller.http.*
import org.hyperledger.identus.presentproof.controller.PresentProofController.toDidCommID
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.util.UUID
import scala.language.implicitConversions

class PresentProofControllerImpl(
    presentationService: PresentationService,
    connectionService: ConnectionService,
    managedDIDService: ManagedDIDService,
    appConfig: AppConfig
) extends PresentProofController
    with ControllerHelper {

  private def checkFeatureFlag(credentialFormat: CredentialFormat) = for {
    _ <- credentialFormat match // Fail if feature is disabled
      case JWT =>
        appConfig.featureFlag.ifJWTIsDisable(
          ZIO.fail(UnsupportedCredentialFormatBecauseDisabled(FeatureFlagConfig.messageIfDisableForJWT))
        )
      case SDJWT =>
        appConfig.featureFlag.ifSDJWTIsDisable(
          ZIO.fail(UnsupportedCredentialFormatBecauseDisabled(FeatureFlagConfig.messageIfDisableForSDJWT))
        )
      case AnonCreds =>
        appConfig.featureFlag.ifAnoncredIsDisable(
          ZIO.fail(UnsupportedCredentialFormatBecauseDisabled(FeatureFlagConfig.messageIfDisableForAnoncred))
        )
  } yield ()

  override def requestPresentation(request: RequestPresentationInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus] = {
    val result: ZIO[WalletAccessContext, ConnectionServiceError | PresentationError, PresentationStatus] =
      for {
        _ <- checkFeatureFlag(request.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT))
        connectionId <- ZIO
          .fromOption(request.connectionId)
          .mapError(_ => PresentationError.MissingConnectionIdForPresentationRequest)
        didIdPair <- getPairwiseDIDs(connectionId).provideSomeLayer(ZLayer.succeed(connectionService))
        record <- createRequestPresentation(
          verifierDID = didIdPair.myDID,
          proverDID = Some(didIdPair.theirDid),
          request = request,
          expirationDuration = None
        )
      } yield PresentationStatus.fromDomain(record)
    result
  }

  override def createOOBRequestPresentationInvitation(request: RequestPresentationInput)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus] = {
    val result: ZIO[WalletAccessContext, ConnectionServiceError | PresentationError, PresentationStatus] = for {
      _ <- checkFeatureFlag(request.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT))
      peerDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      record <- createRequestPresentation(
        verifierDID = peerDid.did,
        proverDID = None,
        request = request,
        expirationDuration = Some(appConfig.pollux.presentationInvitationExpiry)
      )
    } yield PresentationStatus.fromDomain(record)
    result
  }

  private def createRequestPresentation(
      verifierDID: DidId,
      proverDID: Option[DidId],
      request: RequestPresentationInput,
      expirationDuration: Option[Duration]
  ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = {
    createPresentationRecord(
      verifierDID,
      proverDID,
      request.connectionId.map(_.toString),
      request.credentialFormat,
      request.proofs,
      request.options.map(o => Options(o.challenge, o.domain)),
      request.claims,
      request.anoncredPresentationRequest,
      request.presentationFormat,
      request.goalCode,
      request.goal,
      expirationDuration
    )
  }

  private def createPresentationRecord(
      verifierDID: DidId,
      proverDID: Option[DidId],
      connectionId: Option[String],
      credentialFormat: Option[String],
      proofs: Seq[ProofRequestAux],
      options: Option[Options],
      claims: Option[zio.json.ast.Json.Obj],
      anoncredPresentationRequest: Option[AnoncredPresentationRequestV1],
      presentationFormat: Option[PresentCredentialRequestFormat],
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
  ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = {
    val format = credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT)
    format match {
      case CredentialFormat.JWT =>
        presentationService.createJwtPresentationRecord(
          pairwiseVerifierDID = verifierDID,
          pairwiseProverDID = proverDID,
          thid = DidCommID(),
          connectionId = connectionId,
          proofTypes = proofs.map { e =>
            ProofType(
              schema = e.schemaId,
              requiredFields = None,
              trustIssuers = Some(e.trustIssuers.map(DidId(_)))
            )
          },
          options = options,
          presentationFormat = presentationFormat.getOrElse(PresentCredentialRequestFormat.JWT),
          goalCode = goalCode,
          goal = goal,
          expirationDuration = expirationDuration,
        )
      case CredentialFormat.SDJWT =>
        claims match {
          case Some(claimsToDisclose) =>
            presentationService.createSDJWTPresentationRecord(
              pairwiseVerifierDID = verifierDID,
              pairwiseProverDID = proverDID,
              thid = DidCommID(),
              connectionId = connectionId,
              proofTypes = proofs.map { e =>
                ProofType(
                  schema = e.schemaId,
                  requiredFields = None,
                  trustIssuers = Some(e.trustIssuers.map(DidId(_)))
                )
              },
              claimsToDisclose = claimsToDisclose,
              options = options,
              presentationFormat = presentationFormat.getOrElse(PresentCredentialRequestFormat.SDJWT),
              goalCode = goalCode,
              goal = goal,
              expirationDuration = expirationDuration,
            )
          case None =>
            ZIO.fail(
              PresentationError.MissingSDJWTPresentationRequest(
                "presentation request is missing claims to be disclosed"
              )
            )
        }
      case CredentialFormat.AnonCreds =>
        anoncredPresentationRequest match {
          case Some(presentationRequest) =>
            presentationService.createAnoncredPresentationRecord(
              pairwiseVerifierDID = verifierDID,
              pairwiseProverDID = proverDID,
              thid = DidCommID(),
              connectionId = connectionId,
              presentationRequest = presentationRequest,
              presentationFormat = presentationFormat.getOrElse(PresentCredentialRequestFormat.Anoncred),
              goalCode = goalCode,
              goal = goal,
              expirationDuration = expirationDuration,
            )
          case None =>
            ZIO.fail(
              PresentationError.MissingAnoncredPresentationRequest("Anoncred presentation request is missing")
            )
        }
    }
  }

  override def getPresentations(paginationInput: PaginationInput, thid: Option[String])(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatusPage] = {
    val result = for {
      records <- thid match
        case None       => presentationService.getPresentationRecords(ignoreWithZeroRetries = false)
        case Some(thid) => presentationService.findPresentationRecordByThreadId(DidCommID(thid)).map(_.toSeq)
    } yield PresentationStatusPage(
      records.map(PresentationStatus.fromDomain)
    )

    result
  }

  override def getPresentation(
      id: UUID
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus] = {
    val result: ZIO[WalletAccessContext, ErrorResponse | PresentationError, PresentationStatus] = for {
      presentationId <- toDidCommID(id.toString)
      maybeRecord <- presentationService.findPresentationRecord(presentationId)
      record <- ZIO
        .fromOption(maybeRecord)
        .mapError(_ => ErrorResponse.notFound(detail = Some(s"Presentation record not found: $id")))
    } yield PresentationStatus.fromDomain(record)
    result
  }

  override def updatePresentation(id: UUID, requestPresentationAction: RequestPresentationAction)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus] = {
    val result: ZIO[WalletAccessContext, ErrorResponse | PresentationError, PresentationStatus] = for {
      didCommId <- ZIO.succeed(DidCommID(id.toString))
      record <- requestPresentationAction.action match {
        case "request-accept" =>
          (requestPresentationAction.proofId, requestPresentationAction.anoncredPresentationRequest) match
            case (Some(proofs), None) => //// TODO based on CredentialFormat
              val credentialFormat =
                requestPresentationAction.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT)
              credentialFormat match
                case CredentialFormat.SDJWT =>
                  presentationService.acceptSDJWTRequestPresentation(
                    recordId = didCommId,
                    credentialsToUse = proofs,
                    claimsToDisclose = requestPresentationAction.claims
                  )
                case _ => presentationService.acceptRequestPresentation(recordId = didCommId, credentialsToUse = proofs)
            case (None, Some(proofs)) => // TODO based on CredentialFormat Not sure why this was done like this
              presentationService.acceptAnoncredRequestPresentation(
                recordId = didCommId,
                credentialsToUse = proofs
              )
            case _ => presentationService.acceptRequestPresentation(recordId = didCommId, credentialsToUse = Seq())

        case "request-reject"      => presentationService.rejectRequestPresentation(didCommId)
        case "presentation-accept" => presentationService.acceptPresentation(didCommId)
        case "presentation-reject" => presentationService.rejectPresentation(didCommId)
        case a                     =>
          ZIO.fail(
            ErrorResponse.badRequest(
              detail = Some(
                s"presentation action must be 'request-accept','request-reject', 'presentation-accept', or 'presentation-reject' but is $a"
              )
            )
          )
      }
    } yield PresentationStatus.fromDomain(record)

    result
  }

  def acceptRequestPresentationInvitation(
      request: AcceptRequestPresentationInvitation
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, PresentationStatus] = {
    for {
      pairwiseDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      requestPresentation <- presentationService.getRequestPresentationFromInvitation(
        pairwiseDid.did,
        request.invitation
      )
      record <- presentationService.receiveRequestPresentation(
        None, // connectionless hence none
        requestPresentation
      ) // TODO should we store the invitation in prover db ???
    } yield PresentationStatus.fromDomain(record)
  }
}

object PresentProofControllerImpl {
  val layer: URLayer[PresentationService & ConnectionService & ManagedDIDService & AppConfig, PresentProofController] =
    ZLayer.fromFunction(PresentProofControllerImpl(_, _, _, _))
}
