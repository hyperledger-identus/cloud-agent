package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.agent.server.config.{AppConfig, FeatureFlagConfig}
import org.hyperledger.identus.agent.server.ControllerHelper
import org.hyperledger.identus.agent.walletapi.model.PublicationState
import org.hyperledger.identus.agent.walletapi.model.PublicationState.{Created, PublicationPending, Published}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.{CollectionStats, PaginationInput}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.castor.core.model.did.{DIDUrl, PrismDID, VerificationRelationship}
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.connect.core.service.ConnectionService
import org.hyperledger.identus.issue.controller.http.*
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.pollux.core.model.{CredentialFormat, DidCommID, ResourceResolutionMethod}
import org.hyperledger.identus.pollux.core.model.CredentialFormat.{AnonCreds, JWT, SDJWT}
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.Role
import org.hyperledger.identus.pollux.core.service.{CredentialDefinitionService, CredentialService}
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.json.Json as JsonUtils
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import org.hyperledger.identus.shared.utils.Base64Utils
import zio.*
import zio.json.given

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

class IssueControllerImpl(
    credentialService: CredentialService,
    credentialDefinitionService: CredentialDefinitionService,
    connectionService: ConnectionService,
    didService: DIDService,
    managedDIDService: ManagedDIDService,
    appConfig: AppConfig
) extends IssueController
    with ControllerHelper
    with CredentialSchemaReferenceParsingLogic {

  private case class OfferContext(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration]
  )

  private def checkFeatureFlag(credentialFormat: CredentialFormat) = for {
    _ <- credentialFormat match // Fail if feature is disabled
      case JWT =>
        appConfig.featureFlag.ifJWTIsDisable(
          ZIO.fail(ErrorResponse.badRequestDisabled(FeatureFlagConfig.messageIfDisableForJWT))
        )
      case SDJWT =>
        appConfig.featureFlag.ifSDJWTIsDisable(
          ZIO.fail(ErrorResponse.badRequestDisabled(FeatureFlagConfig.messageIfDisableForSDJWT))
        )
      case AnonCreds =>
        appConfig.featureFlag.ifAnoncredIsDisable(
          ZIO.fail(ErrorResponse.badRequestDisabled(FeatureFlagConfig.messageIfDisableForAnoncred))
        )
  } yield ()

  private def getIssuingDIDFromRequestJwtProperties(
      request: CreateIssueCredentialRecordRequest
  ): IO[ErrorResponse, PrismDID] =
    ZIO
      .fromOption(request.jwtVcPropertiesV1.map(_.issuingDID))
      .orElse(ZIO.fromOption(request.issuingDID))
      .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: issuingDID")))
      .flatMap(extractPrismDIDFromString)

  private def getIssuingDIDFromRequestSDJWTProperties(
      request: CreateIssueCredentialRecordRequest
  ): IO[ErrorResponse, PrismDID] =
    ZIO
      .fromOption(request.sdJwtVcPropertiesV1.map(_.issuingDID))
      .orElse(ZIO.fromOption(request.issuingDID))
      .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: issuingDID")))
      .flatMap(extractPrismDIDFromString)

  private def getIssuingDIDFromAnonCredsProperties(
      request: CreateIssueCredentialRecordRequest
  ): IO[ErrorResponse, PrismDID] =
    ZIO
      .fromOption(request.anoncredsVcPropertiesV1.map(_.issuingDID))
      .orElse(ZIO.fromOption(request.issuingDID))
      .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: issuingDID")))
      .flatMap(extractPrismDIDFromString)

  private def createCredentialOfferRecord(
      request: CreateIssueCredentialRecordRequest,
      offerContext: OfferContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {

    for {
      credentialFormat <- ZIO.succeed(
        request.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT)
      )
      _ <- checkFeatureFlag(credentialFormat)
      outcome <-
        credentialFormat match
          case JWT =>
            for {
              issuingDID <- getIssuingDIDFromRequestJwtProperties(request)
              _ <- validatePrismDID(issuingDID, allowUnpublished = true, Role.Issuer)
              credentialSchemaRef <- parseCredentialSchemaRef_VCDM1_1(
                request.schemaId,
                request.jwtVcPropertiesV1.map(_.credentialSchema)
              )
              claims <- ZIO
                .fromOption(request.jwtVcPropertiesV1.map(_.claims).orElse(request.claims))
                .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: claims")))
              kid = request.jwtVcPropertiesV1
                .flatMap(_.issuingKid)
                .orElse(request.issuingKid) // TODO: should it be Option[KeyId]?
              validityPeriod = request.jwtVcPropertiesV1
                .flatMap(_.validityPeriod)
                .orElse(request.validityPeriod)
              record <- credentialService
                .createJWTIssueCredentialRecord(
                  pairwiseIssuerDID = offerContext.pairwiseIssuerDID,
                  pairwiseHolderDID = offerContext.pairwiseHolderDID,
                  kidIssuer = kid,
                  thid = DidCommID(),
                  credentialSchemaRef = Some(credentialSchemaRef),
                  claims = claims,
                  validityPeriod = validityPeriod,
                  automaticIssuance = request.automaticIssuance.orElse(Some(true)),
                  issuingDID = issuingDID.asCanonical,
                  goalCode = offerContext.goalCode,
                  goal = offerContext.goal,
                  expirationDuration = offerContext.expirationDuration,
                  connectionId = request.connectionId,
                  domain = request.domain.getOrElse(appConfig.pollux.defaultJwtVCOfferDomain)
                )
            } yield record
          case SDJWT =>
            for {
              issuingDID <- getIssuingDIDFromRequestSDJWTProperties(request)
              _ <- validatePrismDID(issuingDID, allowUnpublished = true, Role.Issuer)
              credentialSchemaRef <- parseCredentialSchemaRef_VCDM1_1(
                request.schemaId,
                request.sdJwtVcPropertiesV1.map(_.credentialSchema)
              )
              claims <- ZIO
                .fromOption(request.sdJwtVcPropertiesV1.map(_.claims).orElse(request.claims))
                .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: claims")))
              kid = request.sdJwtVcPropertiesV1
                .flatMap(_.issuingKid)
                .orElse(request.issuingKid) // TODO: should it be Option[KeyId]?
              validityPeriod = request.sdJwtVcPropertiesV1
                .flatMap(_.validityPeriod)
                .orElse(request.validityPeriod)
              record <- credentialService
                .createSDJWTIssueCredentialRecord(
                  pairwiseIssuerDID = offerContext.pairwiseIssuerDID,
                  pairwiseHolderDID = offerContext.pairwiseHolderDID,
                  kidIssuer = kid,
                  thid = DidCommID(),
                  credentialSchemaRef = Option(credentialSchemaRef),
                  claims = claims,
                  validityPeriod = request.validityPeriod,
                  automaticIssuance = request.automaticIssuance.orElse(Some(true)),
                  issuingDID = issuingDID.asCanonical,
                  goalCode = offerContext.goalCode,
                  goal = offerContext.goal,
                  expirationDuration = offerContext.expirationDuration,
                  connectionId = request.connectionId,
                  domain = request.domain.getOrElse(appConfig.pollux.defaultJwtVCOfferDomain)
                )
            } yield record
          case AnonCreds =>
            for {
              issuingDID <- getIssuingDIDFromAnonCredsProperties(request)
              credentialDefinitionGUID <- ZIO
                .fromOption(
                  request.anoncredsVcPropertiesV1
                    .map(_.credentialDefinitionId)
                    .orElse(request.credentialDefinitionId)
                )
                .mapError(_ =>
                  ErrorResponse.badRequest(detail = Some("Missing request parameter: credentialDefinitionId"))
                )
              credentialDefinition <- credentialDefinitionService.getByGUID(credentialDefinitionGUID)
              credentialDefinitionId <- {

                credentialDefinition.resolutionMethod match
                  case ResourceResolutionMethod.did =>
                    val publicEndpointServiceName = appConfig.agent.httpEndpoint.serviceName
                    val didUrlResourcePath =
                      s"credential-definition-registry/definitions/did-url/${credentialDefinitionGUID.toString}/definition"
                    val didUrl = for {
                      canonicalized <- JsonUtils.canonicalizeToJcs(credentialDefinition.definition.toJson)
                      encoded = Base64Utils.encodeURL(canonicalized.getBytes)
                      hash = Sha256Hash.compute(encoded.getBytes).hexEncoded
                      didUrl = DIDUrl(
                        issuingDID.did,
                        Seq(),
                        ListMap(
                          "resourceService" -> Seq(publicEndpointServiceName),
                          "resourcePath" -> Seq(
                            s"$didUrlResourcePath?resourceHash=$hash"
                          ),
                        ),
                        None
                      ).toString
                    } yield didUrl

                    ZIO
                      .fromEither(didUrl)
                      .mapError(_ => ErrorResponse.badRequest(detail = Some("Could not parse credential definition")))

                  case ResourceResolutionMethod.http =>
                    val publicEndpointUrl = appConfig.agent.httpEndpoint.publicEndpointUrl.toExternalForm
                    val httpUrlSuffix =
                      s"credential-definition-registry/definitions/${credentialDefinitionGUID.toString}/definition"
                    val urlPrefix = if (publicEndpointUrl.endsWith("/")) publicEndpointUrl else publicEndpointUrl + "/"
                    ZIO.succeed(s"$urlPrefix$httpUrlSuffix")
              }
              claims <- ZIO
                .fromOption(request.anoncredsVcPropertiesV1.map(_.claims).orElse(request.claims))
                .orElseFail(ErrorResponse.badRequest(detail = Some("Missing request parameter: claims")))
              validityPeriod = request.anoncredsVcPropertiesV1
                .flatMap(_.validityPeriod)
                .orElse(request.validityPeriod)
              record <- credentialService
                .createAnonCredsIssueCredentialRecord(
                  pairwiseIssuerDID = offerContext.pairwiseIssuerDID,
                  pairwiseHolderDID = offerContext.pairwiseHolderDID,
                  thid = DidCommID(),
                  credentialDefinitionGUID = credentialDefinitionGUID,
                  credentialDefinitionId = credentialDefinitionId,
                  claims = claims,
                  validityPeriod = validityPeriod,
                  automaticIssuance = request.automaticIssuance.orElse(Some(true)),
                  goalCode = offerContext.goalCode,
                  goal = offerContext.goal,
                  expirationDuration = offerContext.expirationDuration,
                  connectionId = request.connectionId
                )
            } yield record

    } yield IssueCredentialRecord.fromDomain(outcome)
  }

  override def createCredentialOffer(
      request: CreateIssueCredentialRecordRequest
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {

    for {
      connectionId <- ZIO
        .fromOption(request.connectionId)
        .mapError(_ => ErrorResponse.badRequest(detail = Some("Missing connectionId for credential offer")))
      _ <- checkFeatureFlag(request.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT))
      didIdPair <- getPairwiseDIDs(connectionId).provideSomeLayer(ZLayer.succeed(connectionService))
      offerContext = OfferContext(
        pairwiseIssuerDID = didIdPair.myDID,
        pairwiseHolderDID = Some(didIdPair.theirDid),
        goalCode = None,
        goal = None,
        expirationDuration = None
      )
      result <- createCredentialOfferRecord(request, offerContext)
    } yield result
  }

  override def createCredentialOfferInvitation(
      request: CreateIssueCredentialRecordRequest
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {
    for {
      peerDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      _ <- checkFeatureFlag(request.credentialFormat.map(CredentialFormat.valueOf).getOrElse(CredentialFormat.JWT))
      offerContext = OfferContext(
        pairwiseIssuerDID = peerDid.did,
        pairwiseHolderDID = None,
        goalCode = request.goalCode,
        goal = request.goal,
        expirationDuration = Some(appConfig.pollux.issuanceInvitationExpiry)
      )
      result <- createCredentialOfferRecord(request, offerContext)
    } yield result
  }

  def acceptCredentialOfferInvitation(
      request: AcceptCredentialOfferInvitation
  )(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {
    for {
      peerDid <- managedDIDService.createAndStorePeerDID(appConfig.agent.didCommEndpoint.publicEndpointUrl)
      credentialOffer <- credentialService.getCredentialOfferInvitation(
        peerDid.did,
        request.invitation
      )
      record <- credentialService.receiveCredentialOffer(credentialOffer)
    } yield IssueCredentialRecord.fromDomain(record)
  }

  override def getCredentialRecords(paginationInput: PaginationInput, thid: Option[String])(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecordPage] = {
    val uri = rc.request.uri
    val pagination = paginationInput.toPagination
    for {
      pageResult <- thid match
        case None =>
          credentialService
            .getIssueCredentialRecords(
              ignoreWithZeroRetries = false,
              offset = Some(pagination.offset),
              limit = Some(pagination.limit)
            )
        case Some(thid) =>
          credentialService
            .getIssueCredentialRecordByThreadId(DidCommID(thid), ignoreWithZeroRetries = false)
            .map(_.toSeq)
            .map(records => records -> records.length)
      (records, totalCount) = pageResult
      stats = CollectionStats(totalCount = totalCount, filteredCount = totalCount)
    } yield IssueCredentialRecordPage(
      self = uri.toString(),
      kind = "Collection",
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      next = PaginationUtils.composeNextUri(uri, records, pagination, stats).map(_.toString),
      previous = PaginationUtils.composePreviousUri(uri, records, pagination, stats).map(_.toString),
      contents = records map IssueCredentialRecord.fromDomain
    )
  }

  override def getCredentialRecord(
      recordId: String
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {
    for {
      id <- extractDidCommIdFromString(recordId)
      outcome <- credentialService.getById(id)
    } yield IssueCredentialRecord.fromDomain(outcome)
  }

  override def acceptCredentialOffer(recordId: String, request: AcceptCredentialOfferRequest)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {
    for {
      _ <- request.subjectId match
        case Some(did) => extractPrismDIDFromString(did).flatMap(validatePrismDID(_, true, Role.Holder))
        case None      => ZIO.succeed(())
      id <- extractDidCommIdFromString(recordId)
      outcome <- credentialService.acceptCredentialOffer(id, request.subjectId, request.keyId.map(KeyId(_)))
    } yield IssueCredentialRecord.fromDomain(outcome)
  }

  override def issueCredential(
      recordId: String
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, IssueCredentialRecord] = {
    for {
      id <- extractDidCommIdFromString(recordId)
      outcome <- credentialService.acceptCredentialRequest(id)
    } yield IssueCredentialRecord.fromDomain(outcome)
  }

  private def validatePrismDID(
      prismDID: PrismDID,
      allowUnpublished: Boolean,
      role: Role
  ): ZIO[WalletAccessContext, ErrorResponse, Unit] = {
    for {
      maybeDIDState <- managedDIDService
        .getManagedDIDState(prismDID.asCanonical)
        .orDieWith(e => RuntimeException(s"Error occurred while getting DID from wallet: ${e.toString}"))
      initialResolveDID <- didService
        .resolveDID(prismDID)
        .orDieWith(e => RuntimeException(s"Error occurred while resolving the Prism DID: ${e.toString}"))
      oInitialDidData = initialResolveDID.map(_._2)
      mayBeResolveWithLongFormOrShortForm <-
        if (oInitialDidData.isEmpty) {
          for {
            oLongFormDid <- getLongFormPrismDID(prismDID, allowUnpublished)
              .orDieWith(e => RuntimeException(s"Error occurred while getting the long form Prism DID: ${e.toString}"))
              .provideSomeLayer(ZLayer.succeed(managedDIDService))
            longFormDid <- ZIO
              .fromOption(oLongFormDid)
              .orElse(ZIO.dieMessage(s"Longform of Prism DID cannot be found: $oLongFormDid"))
            resolvedDID <- didService
              .resolveDID(longFormDid)
              .orDieWith(e => RuntimeException(s"Error occurred while resolving the Prism DID: ${e.toString}"))
          } yield resolvedDID
        } else {
          ZIO.succeed(initialResolveDID)
        }
      maybeDidData = mayBeResolveWithLongFormOrShortForm.map(_._2)
      maybeMetadata = mayBeResolveWithLongFormOrShortForm.map(_._1)
      _ <- ZIO.when(role == Role.Holder) {
        ZIO
          .fromOption(maybeDidData.flatMap(_.publicKeys.find(_.purpose == VerificationRelationship.Authentication)))
          .orElseFail(ErrorResponse.badRequest(detail = Some(s"Authentication key not found for the $prismDID")))
      }
      _ <- ZIO.when(role == Role.Issuer)(
        ZIO
          .fromOption(maybeDidData.flatMap(_.publicKeys.find(_.purpose == VerificationRelationship.AssertionMethod)))
          .orElseFail(ErrorResponse.badRequest(detail = Some(s"AssertionMethod key not found for the $prismDID")))
      )
      _ <- (maybeDIDState.map(_.publicationState), maybeMetadata.map(_.deactivated)) match {
        case (None, _) =>
          ZIO.fail(ErrorResponse.badRequest(detail = Some("The provided DID can't be found in the agent wallet")))

        case (Some(Created() | PublicationPending(_)), _) if allowUnpublished =>
          ZIO.succeed(())

        case (Some(Created() | PublicationPending(_)), _) =>
          ZIO.fail(ErrorResponse.badRequest(detail = Some("The provided DID is not published")))

        case (Some(Published(_)), None) =>
          ZIO.succeed(())

        case (Some(Published(_)), Some(true)) =>
          ZIO.fail(ErrorResponse.badRequest(detail = Some("The provided DID is published but deactivated")))

        case (Some(Published(_)), Some(false)) =>
          ZIO.succeed(())
      }
    } yield ()
  }
}

object IssueControllerImpl {
  val layer: URLayer[
    CredentialService & CredentialDefinitionService & ConnectionService & DIDService & ManagedDIDService & AppConfig,
    IssueController
  ] =
    ZLayer.fromFunction(IssueControllerImpl(_, _, _, _, _, _))
}
