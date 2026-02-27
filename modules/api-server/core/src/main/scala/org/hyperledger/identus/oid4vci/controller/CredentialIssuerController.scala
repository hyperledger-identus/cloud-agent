package org.hyperledger.identus.oid4vci.controller

import org.hyperledger.identus.agent.server.config.AppConfig
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.ErrorResponse.{badRequest, internalServerError}
import org.hyperledger.identus.api.util.PaginationUtils
import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.oid4vci.domain.Openid4VCIProofJwtOps
import org.hyperledger.identus.oid4vci.http.*
import org.hyperledger.identus.oid4vci.http.CredentialErrorCode.*
import org.hyperledger.identus.oid4vci.service.OIDCCredentialIssuerService
import org.hyperledger.identus.pollux.core.model.oid4vci.CredentialIssuer as PolluxCredentialIssuer
import org.hyperledger.identus.pollux.core.service.OID4VCIIssuerMetadataService
import org.hyperledger.identus.pollux.vc.jwt.JWT
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{IO, URLayer, ZIO, ZLayer}

import java.net.{URI, URL}
import java.util.UUID
import scala.language.implicitConversions

trait CredentialIssuerController {
  def issueCredential(
      ctx: RequestContext,
      issuerId: UUID,
      credentialRequest: CredentialRequest
  ): IO[ExtendedErrorResponse, CredentialResponse]

  def createCredentialOffer(
      ctx: RequestContext,
      issuerId: UUID,
      credentialOfferRequest: CredentialOfferRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialOfferResponse]

  def getNonce(
      ctx: RequestContext,
      request: NonceRequest
  ): IO[ErrorResponse, NonceResponse]

  def createCredentialIssuer(
      ctx: RequestContext,
      request: CreateCredentialIssuerRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuer]

  def getCredentialIssuers(
      ctx: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuerPage]

  def updateCredentialIssuer(
      ctx: RequestContext,
      issuerId: UUID,
      request: PatchCredentialIssuerRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuer]

  def deleteCredentialIssuer(
      ctx: RequestContext,
      issuerId: UUID,
  ): ZIO[WalletAccessContext, ErrorResponse, Unit]

  def createCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      request: CreateCredentialConfigurationRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialConfiguration]

  def getCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialConfiguration]

  def deleteCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, ErrorResponse, Unit]

  def getIssuerMetadata(
      ctx: RequestContext,
      issuerId: UUID
  ): IO[ErrorResponse, IssuerMetadata]
}

object CredentialIssuerController {
  object Errors {
    def badRequestInvalidDID(didRef: String, details: String): ExtendedErrorResponse =
      CredentialErrorResponse(
        error = invalid_request,
        errorDescription = Some(s"Invalid DID input: $didRef. Error: $details")
      )

    def badRequestDIDResolutionFailed(didRef: String, details: String): ExtendedErrorResponse =
      CredentialErrorResponse(
        error = invalid_request,
        errorDescription = Some(s"Failed to resolve DID: $didRef. Error: $details")
      )

    def badRequestInvalidProof(jwt: String, details: String): ExtendedErrorResponse =
      CredentialErrorResponse(error = invalid_proof, errorDescription = Some(s"Invalid proof: $jwt. Error: $details"))

    def badRequestUnsupportedCredentialFormat(format: CredentialFormat): ExtendedErrorResponse =
      CredentialErrorResponse(
        error = unsupported_credential_format,
        errorDescription = Some(s"Unsupported credential format: $format")
      )

    def badRequestUnsupportedCredentialType(details: String): ExtendedErrorResponse =
      CredentialErrorResponse(
        error = unsupported_credential_type,
        errorDescription = Some(s"Unsupported credential type. Error: $details")
      )

    def serverError(details: Option[String]): ExtendedErrorResponse =
      internalServerError("InternalServerError", details)
  }
}

case class CredentialIssuerControllerImpl(
    credentialIssuerService: OIDCCredentialIssuerService,
    issuerMetadataService: OID4VCIIssuerMetadataService,
    agentBaseUrl: URL
) extends CredentialIssuerController
    with Openid4VCIProofJwtOps {

  import CredentialIssuerController.Errors.*
  import OIDCCredentialIssuerService.Errors.*

  private def parseAbsoluteURL(url: String): IO[ErrorResponse, URL] =
    ZIO
      .attempt(URI.create(url))
      .mapError(ue => badRequest(detail = Some(s"Invalid URL: $url")))
      .filterOrFail(_.isAbsolute())(badRequest(detail = Some(s"Relative URL '$url' is not allowed")))
      .map(_.toURL())

  private def baseCredentialIssuerUrl(issuerId: UUID): URL =
    URI(s"$agentBaseUrl/oid4vci/issuers/$issuerId").toURL()

  private def redactedAuthServer(as: AuthorizationServer): String =
    s"url=${Option(as.url).getOrElse("<none>")} clientId=${Option(as.clientId).getOrElse("<none>")} clientSecret=<redacted>"

  private def redactedAuthServer(as: PatchAuthorizationServer): String =
    s"url=${as.url.getOrElse("<unchanged>")} clientId=${as.clientId.getOrElse("<unchanged>")} clientSecret=<redacted>"

  def issueCredential(
      ctx: RequestContext,
      issuerId: UUID,
      credentialRequest: CredentialRequest
  ): IO[ExtendedErrorResponse, CredentialResponse] = {
    credentialRequest match
      case JwtCredentialRequest(
            format,
            proof,
            credentialIdentifier,
            credentialResponseEncryption,
            credentialDefinition
          ) =>
        issueJwtCredential(issuerId, proof, credentialIdentifier, credentialDefinition, credentialResponseEncryption)
      case other: CredentialRequest => // add other formats here
        ZIO.fail(badRequestUnsupportedCredentialFormat(credentialRequest.format))
  }

  def issueJwtCredential(
      issuerId: UUID,
      maybeProof: Option[Proof],
      maybeCredentialIdentifier: Option[String],
      maybeCredentialDefinition: Option[CredentialDefinition],
      maybeEncryption: Option[CredentialResponseEncryption]
  ): IO[ExtendedErrorResponse, CredentialResponse] = {
    maybeProof match {
      case Some(JwtProof(proofType, jwt)) =>
        for {
          _ <- ZIO
            .ifZIO(credentialIssuerService.verifyJwtProof(JWT(jwt)))(
              ZIO.unit,
              ZIO.fail(OIDCCredentialIssuerService.Errors.InvalidProof("Invalid proof"))
            )
            .mapError {
              case InvalidProof(message)       => badRequestInvalidProof(jwt, message)
              case DIDResolutionError(message) => badRequestInvalidProof(jwt, message)
            }
          nonce <- getNonceFromJwt(JWT(jwt))
            .mapError(throwable => badRequestInvalidProof(jwt, throwable.getMessage))
          session <- credentialIssuerService
            .getPendingIssuanceSessionByNonce(nonce)
            .mapError(_ => badRequestInvalidProof(jwt, "nonce is not associated to the issuance session"))
          subjectDid <- parseDIDUrlFromKeyId(JWT(jwt))
            .map(_.did)
            .mapError(throwable => badRequestInvalidProof(jwt, throwable.getMessage))
          sessionWithSubjectDid <- credentialIssuerService
            .updateIssuanceSession(session.withSubjectDid(subjectDid))
            .mapError(ue =>
              serverError(
                Some(s"Unexpected error while updating issuance session with subject DID: ${ue.userFacingMessage}")
              )
            )
          credentialDefinition <- ZIO
            .fromOption(maybeCredentialDefinition)
            .mapError(_ => badRequestUnsupportedCredentialType("No credential definition provided"))
          validatedCredentialDefinition <- credentialIssuerService
            .validateCredentialDefinition(credentialDefinition)
            .mapError(ue =>
              serverError(Some(s"Unexpected error while validating credential definition: ${ue.userFacingMessage}"))
            )
          credential <- credentialIssuerService
            .issueJwtCredential(
              sessionWithSubjectDid.issuingDid,
              sessionWithSubjectDid.subjectDid,
              sessionWithSubjectDid.claims,
              maybeCredentialIdentifier,
              validatedCredentialDefinition
            )
            .mapError(ue => serverError(Some(s"Unexpected error while issuing credential: ${ue.userFacingMessage}")))
        } yield ImmediateCredentialResponse(credential.value)
      case None                 => ZIO.fail(badRequestInvalidProof(jwt = "empty", details = "No proof provided"))
      case Some(CwtProof(_, _)) => FeatureNotImplemented
      case Some(LdpProof(_, _)) => FeatureNotImplemented
    }
  }

  override def createCredentialOffer(
      ctx: RequestContext,
      issuerId: UUID,
      credentialOfferRequest: CredentialOfferRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialOfferResponse] = {
    for {
      issuingDid <- ZIO
        .fromEither(PrismDID.fromString(credentialOfferRequest.issuingDID))
        .mapError(e => badRequest(detail = Some(s"Invalid DID: $e")))
      resp <- credentialIssuerService
        .createCredentialOffer(
          baseCredentialIssuerUrl(issuerId),
          issuerId,
          credentialOfferRequest.credentialConfigurationId,
          issuingDid,
          credentialOfferRequest.claims
        )
        .map(offer => CredentialOfferResponse(offer.offerUri))
        .mapError(ue =>
          internalServerError(detail =
            Some(s"Unexpected error while creating credential offer: ${ue.userFacingMessage}")
          )
        )
    } yield resp
  }

  override def getNonce(
      ctx: RequestContext,
      request: NonceRequest
  ): IO[ErrorResponse, NonceResponse] = {
    credentialIssuerService
      .getPendingIssuanceSessionByIssuerState(request.issuerState)
      .map(session => NonceResponse(session.nonce))
      .mapError(ue =>
        internalServerError(detail = Some(s"Unexpected error while creating credential offer: ${ue.userFacingMessage}"))
      )
      // Ideally we don't want this here, but this is used by keycloak plugin and error is not bubbled to the user.
      // We log it manually to help with debugging until we find a better way.
      .tapError(error => ZIO.logWarning(error.toString()))
  }

  override def createCredentialIssuer(
      ctx: RequestContext,
      request: CreateCredentialIssuerRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuer] =
    for {
      _ <- ZIO.logInfo(s"[oid4vci] create issuer ${redactedAuthServer(request.authorizationServer)}")
      authServerUrl <- parseAbsoluteURL(request.authorizationServer.url)
      id = request.id.getOrElse(UUID.randomUUID())
      issuerToCreate = PolluxCredentialIssuer(
        id,
        authServerUrl,
        request.authorizationServer.clientId,
        request.authorizationServer.clientSecret
      )
      issuer <- issuerMetadataService.createCredentialIssuer(issuerToCreate)
      _ <- ZIO.logInfo(s"[oid4vci] created issuer id=${issuer.id}")
    } yield issuer

  override def getCredentialIssuers(
      ctx: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuerPage] =
    val uri = ctx.request.uri
    for {
      _ <- ZIO.logDebug(s"[oid4vci] list issuers path=${uri}")
      issuers <- issuerMetadataService.getCredentialIssuers
    } yield CredentialIssuerPage(
      self = uri.toString(),
      pageOf = PaginationUtils.composePageOfUri(uri).toString,
      contents = issuers.map(i => i)
    )

  override def updateCredentialIssuer(
      ctx: RequestContext,
      issuerId: UUID,
      request: PatchCredentialIssuerRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialIssuer] =
    for {
      _ <- ZIO.logInfo(
        s"[oid4vci] update issuer id=$issuerId authServer=${request.authorizationServer.map(redactedAuthServer).getOrElse("<unchanged>")}"
      )
      maybeAuthServerUrl <- ZIO
        .succeed(request.authorizationServer.flatMap(_.url))
        .flatMap {
          case Some(url) => parseAbsoluteURL(url).asSome
          case None      => ZIO.none
        }
      issuer <- issuerMetadataService.updateCredentialIssuer(
        issuerId,
        maybeAuthServerUrl,
        request.authorizationServer.flatMap(_.clientId),
        request.authorizationServer.flatMap(_.clientSecret)
      )
    } yield issuer: CredentialIssuer

  override def deleteCredentialIssuer(
      ctx: RequestContext,
      issuerId: UUID
  ): ZIO[WalletAccessContext, ErrorResponse, Unit] =
    for
      _ <- ZIO.logInfo(s"[oid4vci] delete issuer id=$issuerId")
      _ <- issuerMetadataService.deleteCredentialIssuer(issuerId)
    yield ()

  override def createCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      request: CreateCredentialConfigurationRequest
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialConfiguration] = {
    for {
      credentialConfiguration <- issuerMetadataService.createCredentialConfiguration(
        issuerId,
        request.format,
        request.configurationId,
        request.schemaId
      )
    } yield credentialConfiguration: CredentialConfiguration
  }

  override def getCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, ErrorResponse, CredentialConfiguration] =
    for credentialConfiguration <- issuerMetadataService.getCredentialConfigurationById(issuerId, configurationId)
    yield credentialConfiguration: CredentialConfiguration

  override def deleteCredentialConfiguration(
      ctx: RequestContext,
      issuerId: UUID,
      configurationId: String
  ): ZIO[WalletAccessContext, ErrorResponse, Unit] =
    issuerMetadataService.deleteCredentialConfiguration(issuerId, configurationId)

  override def getIssuerMetadata(ctx: RequestContext, issuerId: UUID): IO[ErrorResponse, IssuerMetadata] = {
    for {
      credentialIssuer <- issuerMetadataService.getCredentialIssuer(issuerId)
      credentialConfigurations <- issuerMetadataService.getCredentialConfigurations(issuerId)
    } yield IssuerMetadata.fromIssuer(
      baseCredentialIssuerUrl(issuerId),
      credentialIssuer,
      credentialConfigurations
    )
  }
}

object CredentialIssuerControllerImpl {
  val layer
      : URLayer[AppConfig & OIDCCredentialIssuerService & OID4VCIIssuerMetadataService, CredentialIssuerController] =
    ZLayer.fromZIO(
      for {
        agentBaseUrl <- ZIO.serviceWith[AppConfig](_.agent.httpEndpoint.publicEndpointUrl)
        oidcIssuerService <- ZIO.service[OIDCCredentialIssuerService]
        oidcIssuerMetadataService <- ZIO.service[OID4VCIIssuerMetadataService]
      } yield CredentialIssuerControllerImpl(oidcIssuerService, oidcIssuerMetadataService, agentBaseUrl)
    )
}
