package org.hyperledger.identus.oid4vci.service

import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.{DID, DIDUrl, PrismDID, VerificationRelationship}
import org.hyperledger.identus.oid4vci.domain.{IssuanceSession, Openid4VCIProofJwtOps}
import org.hyperledger.identus.oid4vci.http.*
import org.hyperledger.identus.oid4vci.storage.IssuanceSessionStorage
import org.hyperledger.identus.pollux.core.model.primitives.UriString.toUriString
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.service.{
  CredentialService,
  OID4VCIIssuerMetadataService,
  OID4VCIIssuerMetadataServiceError
}
import org.hyperledger.identus.pollux.vc.jwt.{
  DidResolver,
  Issuer,
  JWT,
  JWTVerification,
  JwtCredential,
  W3cCredentialPayload,
  *
}
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.*
import zio.*
import zio.json.ast.Json
import zio.json.EncoderOps

import java.net.{URI, URL}
import java.time.Instant
import java.util.UUID
import scala.util.Try

// TODO: move to pollux
// OIDC prefix is added to the service name to avoid name conflicts with a similar service CredentialIssuerService
// It would be nice to refactor these services and merge them into one
trait OIDCCredentialIssuerService {

  import OIDCCredentialIssuerService.Error
  import OIDCCredentialIssuerService.Errors.*

  def verifyJwtProof(jwt: JWT): IO[InvalidProof | DIDResolutionError, Boolean]

  def validateCredentialDefinition(
      credentialDefinition: CredentialDefinition
  ): IO[UnexpectedError, CredentialDefinition]

  def issueJwtCredential(
      issuingDID: PrismDID,
      subjectDID: Option[DID],
      claims: Json,
      credentialIdentifier: Option[String],
      credentialDefinition: CredentialDefinition,
  ): IO[Error, JWT]

  def createCredentialOffer(
      credentialIssuerBaseUrl: URL,
      issuerId: UUID,
      credentialConfigurationId: String,
      issuingDID: PrismDID,
      claims: Json,
  ): ZIO[WalletAccessContext, Error, CredentialOffer]

  def getIssuanceSessionByIssuerState(issuerState: String): IO[Error, IssuanceSession]

  def getPendingIssuanceSessionByIssuerState(issuerState: String): IO[Error, IssuanceSession]

  def getPendingIssuanceSessionByNonce(nonce: String): IO[Error, IssuanceSession]

  def updateIssuanceSession(issuanceSession: IssuanceSession): IO[Error, IssuanceSession]
}

object OIDCCredentialIssuerService {
  sealed trait Error extends Failure {
    override val namespace = "OIDCCredentialIssuerService"
    override val statusCode = StatusCode.BadRequest
  }

  // TODO: use shared Failure trait
  object Errors {
    case class InvalidProof(userFacingMessage: String) extends Error

    case class DIDResolutionError(userFacingMessage: String) extends Error

    case class CredentialConfigurationNotFound(issuerId: UUID, credentialConfigurationId: String) extends Error {
      override def userFacingMessage: String =
        s"Credential configuration with id $credentialConfigurationId not found for issuer $issuerId"
    }

    case class IssuanceSessionAlreadyIssued(issuerState: String) extends Error {
      override def userFacingMessage: String =
        s"Issuance session with issuerState $issuerState is already issued"
    }

    case class CredentialSchemaError(cause: org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError)
        extends Error {
      override def userFacingMessage: String = cause.userFacingMessage
    }

    case class ServiceError(userFacingMessage: String) extends Error

    case class UnexpectedError(cause: Throwable) extends Error {
      override def userFacingMessage: String = cause.getMessage // TODO
    }
  }
}

case class OIDCCredentialIssuerServiceImpl(
    didNonSecretStorage: DIDNonSecretStorage,
    credentialService: CredentialService,
    issuerMetadataService: OID4VCIIssuerMetadataService,
    issuanceSessionStorage: IssuanceSessionStorage,
    didResolver: DidResolver,
    uriResolver: UriResolver,
) extends OIDCCredentialIssuerService
    with Openid4VCIProofJwtOps {

  import OIDCCredentialIssuerService.Error
  import OIDCCredentialIssuerService.Errors.*

  private def resolveVerificationMethodByKeyId(didUrl: DIDUrl): IO[DIDResolutionError, VerificationMethod] = {
    for {
      didDocument <- JWTVerification
        .resolve(didUrl.did.toString)(didResolver)
        .flatMap(_.toZIO)
        .mapError(msg => DIDResolutionError(s"Failed to resolve DID: $msg"))
      verificationMethodKeyOpt = didDocument.verificationMethod
        .find(_.id == didUrl.toString)
      assertionMethodKeyOpt = didDocument.assertionMethod
        .filter(m => m.isInstanceOf[VerificationMethod])
        .map(_.asInstanceOf[VerificationMethod])
        .find(_.id == didUrl.toString)
      verificationMethod <- ZIO
        .fromOption(verificationMethodKeyOpt.orElse(assertionMethodKeyOpt))
        .mapError(_ => DIDResolutionError(s"Verification method not found for keyId: ${didUrl.toString}"))
    } yield verificationMethod
  }

  override def verifyJwtProof(jwt: JWT): IO[InvalidProof | DIDResolutionError, Boolean] = {
    for {
      algorithm <- getAlgorithmFromJwt(jwt)
        .mapError(e => InvalidProof(e.getMessage))
      didUrl <- parseDIDUrlFromKeyId(jwt)
        .mapError(e => InvalidProof(e.getMessage))
      verificationMethod <- resolveVerificationMethodByKeyId(didUrl)
      publicKey <- JWTVerification.extractPublicKey(verificationMethod).toZIO.mapError(InvalidProof.apply)
      _ <- JWTVerification.validateEncodedJwt(jwt, publicKey).toZIO.mapError(InvalidProof.apply)
    } yield true
  }

  override def validateCredentialDefinition(
      credentialDefinition: CredentialDefinition
  ): IO[UnexpectedError, CredentialDefinition] = {
    ZIO.succeed(credentialDefinition) // TODO: implement
  }

  override def issueJwtCredential(
      issuingDID: PrismDID,
      subjectDID: Option[DID],
      claims: Json,
      credentialIdentifier: Option[String],
      credentialDefinition: CredentialDefinition
  ): IO[Error, JWT] = {
    for {
      wac <- didNonSecretStorage
        .getPrismDidWalletId(issuingDID)
        .flatMap(ZIO.fromOption)
        .mapError(_ => ServiceError(s"Failed to get wallet ID for DID: $issuingDID"))
        .map(WalletAccessContext.apply)

      jwtIssuer <- credentialService
        .getJwtIssuer(issuingDID, VerificationRelationship.AssertionMethod, None)
        .provideSomeLayer(ZLayer.succeed(wac))

      jwtVC <- buildJwtVerifiableCredential(
        jwtIssuer.did,
        subjectDID,
        credentialIdentifier,
        credentialDefinition,
        claims
      )
      jwt <- issueJwtVC(jwtIssuer, jwtVC)
    } yield jwt
  }

  def buildJwtVerifiableCredential(
      issuerDid: DID,
      subjectDid: Option[DID],
      credentialIdentifier: Option[String],
      credentialDefinition: CredentialDefinition,
      claims: Json
  ): IO[Error, W3cCredentialPayload] = {
    val credential = W3cCredentialPayload(
      `@context` = Set(
        "https://www.w3.org/2018/credentials/v1"
        // TODO: Add schemaID from schema registry
      ) ++ credentialDefinition.`@context`.getOrElse(Nil), // TODO: Figure out how to validate the context ^^^
      maybeId = credentialIdentifier,
      `type` = Set(
        "VerifiableCredential"
      ) ++ credentialDefinition.`type`, // TODO: This information should come from Schema registry by record.schemaId
      issuer = issuerDid.toString,
      issuanceDate = Instant.now(),
      maybeExpirationDate = None, // TODO: Add expiration date
      maybeCredentialSchema = None, // TODO: Add schema from schema registry
      credentialSubject = buildCredentialSubject(subjectDid, claims),
      maybeCredentialStatus = None, // TODO: Add credential status
      maybeRefreshService = None, // TODO: Add refresh service
      maybeEvidence = None, // TODO: Add evidence
      maybeTermsOfUse = None, // TODO: Add terms of use,
      maybeValidFrom = None, // TODO: Add ValidFrom
      maybeValidUntil = None // TODO: Add ValidUntil
    )

    ZIO.succeed(credential) // TODO: there might be other calls to fill the VC claims from the session, etc
  }

  private def buildCredentialSubject(subjectDid: Option[DID], claims: Json): Json = {
    subjectDid.fold(claims)(did => claims.asObject.get.add("id", Json.Str(did.toString)))
  }

  def issueJwtVC(issuer: Issuer, payload: W3cCredentialPayload): IO[Error, JWT] = {
    ZIO
      .fromTry(Try(JwtCredential.encodeJwt(payload.toJwtCredentialPayload, issuer)))
      .mapError(e => ServiceError(s"Failed to issue JWT: ${e.getMessage}"))
  }

  override def getIssuanceSessionByIssuerState(
      issuerState: String
  ): IO[Error, IssuanceSession] =
    issuanceSessionStorage
      .getByIssuerState(issuerState)
      .mapError(e => ServiceError(s"Failed to get issuance session: ${e.message}"))
      .someOrFail(ServiceError(s"The IssuanceSession with the issuerState $issuerState does not exist"))

  override def getPendingIssuanceSessionByIssuerState(
      issuerState: String
  ): IO[Error, IssuanceSession] = getIssuanceSessionByIssuerState(issuerState).ensurePendingSession

  override def createCredentialOffer(
      credentialIssuerBaseUrl: URL,
      issuerId: UUID,
      credentialConfigurationId: String,
      issuingDID: PrismDID,
      claims: Json
  ): ZIO[WalletAccessContext, Error, CredentialOffer] =
    for {
      credentialSchemaUri <- issuerMetadataService
        .getCredentialConfigurationById(issuerId, credentialConfigurationId)
        .mapError { case _: OID4VCIIssuerMetadataServiceError.CredentialConfigurationNotFound =>
          CredentialConfigurationNotFound(issuerId, credentialConfigurationId)
        }
        .map(_.schemaId)
      _ <- CredentialSchema
        .validateJWTCredentialSubject(credentialSchemaUri.toUriString, claims.toJson, uriResolver)
        .mapError(e => CredentialSchemaError(e))
      session <- buildNewIssuanceSession(issuerId, issuingDID, claims, credentialSchemaUri)
      _ <- issuanceSessionStorage
        .start(session)
        .mapError(e => ServiceError(s"Failed to start issuance session: ${e.message}"))
    } yield CredentialOffer(
      credential_issuer = credentialIssuerBaseUrl.toString(),
      credential_configuration_ids = Seq(credentialConfigurationId),
      grants = Some(
        CredentialOfferGrant(
          authorization_code = CredentialOfferAuthorizationGrant(issuer_state = Some(session.issuerState))
        )
      )
    )

  def getPendingIssuanceSessionByNonce(nonce: String): IO[Error, IssuanceSession] = {
    issuanceSessionStorage
      .getByNonce(nonce)
      .mapError(e => ServiceError(s"Failed to get issuance session: ${e.message}"))
      .someOrFail(ServiceError(s"The IssuanceSession with the nonce $nonce does not exist"))
      .ensurePendingSession
  }

  override def updateIssuanceSession(issuanceSession: IssuanceSession): IO[Error, IssuanceSession] = {
    issuanceSessionStorage
      .update(issuanceSession)
      .mapError(e => ServiceError(s"Failed to update issuance session: ${e.message}"))
  }

  private def buildNewIssuanceSession(
      issuerId: UUID,
      issuerDid: PrismDID,
      claims: Json,
      schemaId: URI
  ): UIO[IssuanceSession] = {
    for {
      id <- ZIO.random.flatMap(_.nextUUID)
      nonce <- ZIO.random.flatMap(_.nextUUID)
      issuerState <- ZIO.random.flatMap(_.nextUUID)
    } yield IssuanceSession(
      id = id,
      issuerId = issuerId,
      nonce = nonce.toString,
      issuerState = issuerState.toString,
      claims = claims,
      schemaId = Some(schemaId.toString),
      subjectDid = None, // FIXME: populate correct value
      issuingDid = issuerDid,
    )
  }

  extension [R, A](result: ZIO[R, Error, IssuanceSession]) {
    def ensurePendingSession: ZIO[R, Error, IssuanceSession] =
      result.flatMap { session =>
        if session.subjectDid.isEmpty
        then ZIO.succeed(session)
        else ZIO.fail(IssuanceSessionAlreadyIssued(session.issuerState))
      }
  }
}

object OIDCCredentialIssuerServiceImpl {
  val layer: URLayer[
    DIDNonSecretStorage & CredentialService & IssuanceSessionStorage & DidResolver & UriResolver &
      OID4VCIIssuerMetadataService,
    OIDCCredentialIssuerService
  ] =
    ZLayer.fromFunction(OIDCCredentialIssuerServiceImpl(_, _, _, _, _, _))
}
