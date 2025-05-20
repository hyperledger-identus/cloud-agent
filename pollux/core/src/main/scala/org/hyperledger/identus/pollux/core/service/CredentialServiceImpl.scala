package org.hyperledger.identus.pollux.core.service

import cats.implicits.*
import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.GenericSecretStorage
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.mercury.model.*
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import org.hyperledger.identus.mercury.protocol.issuecredential.*
import org.hyperledger.identus.pollux.*
import org.hyperledger.identus.pollux.anoncreds.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError.*
import org.hyperledger.identus.pollux.core.model.presentation.*
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.{CredentialDefinition, CredentialSchema, CredentialSchemaRef}
import org.hyperledger.identus.pollux.core.model.secret.CredentialDefinitionSecret
import org.hyperledger.identus.pollux.core.repository.{CredentialRepository, CredentialStatusListRepository}
import org.hyperledger.identus.pollux.prex.{ClaimFormat, Jwt, PresentationDefinition}
import org.hyperledger.identus.pollux.sdjwt.*
import org.hyperledger.identus.pollux.vc.jwt.{Issuer as JwtIssuer, *}
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Secp256k1KeyPair}
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.messaging.{Producer, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.*
import org.hyperledger.identus.shared.models.Failure.orDieAsUnmanagedFailure
import org.hyperledger.identus.shared.utils.aspects.CustomMetricsAspect
import org.hyperledger.identus.shared.utils.Base64Utils
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.prelude.ZValidation

import java.time.{Instant, ZoneId}
import java.util.UUID
import scala.language.implicitConversions

object CredentialServiceImpl {
  val layer: URLayer[
    CredentialRepository & CredentialStatusListRepository & DidResolver & UriResolver & GenericSecretStorage &
      CredentialDefinitionService & LinkSecretService & DIDService & ManagedDIDService &
      Producer[UUID, WalletIdAndRecordId],
    CredentialService
  ] = {
    ZLayer.fromZIO {
      for {
        credentialRepo <- ZIO.service[CredentialRepository]
        credentialStatusListRepo <- ZIO.service[CredentialStatusListRepository]
        didResolver <- ZIO.service[DidResolver]
        uriResolver <- ZIO.service[UriResolver]
        genericSecretStorage <- ZIO.service[GenericSecretStorage]
        credDefenitionService <- ZIO.service[CredentialDefinitionService]
        linkSecretService <- ZIO.service[LinkSecretService]
        didService <- ZIO.service[DIDService]
        manageDidService <- ZIO.service[ManagedDIDService]
        messageProducer <- ZIO.service[Producer[UUID, WalletIdAndRecordId]]
      } yield CredentialServiceImpl(
        credentialRepo,
        credentialStatusListRepo,
        didResolver,
        uriResolver,
        genericSecretStorage,
        credDefenitionService,
        linkSecretService,
        didService,
        manageDidService,
        5,
        messageProducer
      )
    }
  }

  //  private val VC_JSON_SCHEMA_URI = "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json"
  private val VC_JSON_SCHEMA_TYPE = "CredentialSchema2022"
}

class CredentialServiceImpl(
    credentialRepository: CredentialRepository,
    credentialStatusListRepository: CredentialStatusListRepository,
    didResolver: DidResolver,
    uriResolver: UriResolver,
    genericSecretStorage: GenericSecretStorage,
    credentialDefinitionService: CredentialDefinitionService,
    linkSecretService: LinkSecretService,
    didService: DIDService,
    managedDIDService: ManagedDIDService,
    maxRetries: Int = 5, // TODO move to config
    messageProducer: Producer[UUID, WalletIdAndRecordId],
) extends CredentialService {

  import CredentialServiceImpl.*
  import IssueCredentialRecord.*

  private val TOPIC_NAME = "issue"

  override def getIssueCredentialRecords(
      ignoreWithZeroRetries: Boolean,
      offset: Option[Int],
      limit: Option[Int]
  ): URIO[WalletAccessContext, (Seq[IssueCredentialRecord], Int)] =
    credentialRepository.findAll(ignoreWithZeroRetries = ignoreWithZeroRetries, offset = offset, limit = limit)

  override def getIssueCredentialRecordByThreadId(
      thid: DidCommID,
      ignoreWithZeroRetries: Boolean
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] =
    credentialRepository.findByThreadId(thid, ignoreWithZeroRetries)

  override def findById(
      recordId: DidCommID
  ): URIO[WalletAccessContext, Option[IssueCredentialRecord]] =
    credentialRepository.findById(recordId)

  override def getById(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] =
    for {
      maybeRecord <- credentialRepository.findById(recordId)
      record <- ZIO
        .fromOption(maybeRecord)
        .mapError(_ => RecordNotFound(recordId))
    } yield record

  private def createIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      kidIssuer: Option[KeyId],
      thid: DidCommID,
      schemaUris: Option[List[UriString]],
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      issuingDID: Option[CanonicalPrismDID],
      credentialFormat: CredentialFormat,
      offer: OfferCredential,
      credentialDefinitionGUID: Option[UUID] = None,
      credentialDefinitionId: Option[String] = None,
      connectionId: Option[UUID],
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
  ): URIO[WalletAccessContext, IssueCredentialRecord] = {
    for {
      invitation <- ZIO.succeed(
        connectionId.fold(
          Some(
            IssueCredentialInvitation.makeInvitation(
              pairwiseIssuerDID,
              goalCode,
              goal,
              thid.value,
              offer,
              expirationDuration
            )
          )
        )(_ => None)
      )
      record <- ZIO.succeed(
        IssueCredentialRecord(
          id = DidCommID(),
          createdAt = Instant.now,
          updatedAt = None,
          thid = thid,
          schemaUris = schemaUris.map(uris => uris.map(uri => uri.toString)),
          credentialDefinitionId = credentialDefinitionGUID,
          credentialDefinitionUri = credentialDefinitionId,
          credentialFormat = credentialFormat,
          invitation = invitation,
          role = IssueCredentialRecord.Role.Issuer,
          subjectId = None,
          keyId = kidIssuer,
          validityPeriod = validityPeriod,
          automaticIssuance = automaticIssuance,
          protocolState = invitation.fold(IssueCredentialRecord.ProtocolState.OfferPending)(_ =>
            IssueCredentialRecord.ProtocolState.InvitationGenerated
          ),
          offerCredentialData = Some(offer),
          requestCredentialData = None,
          anonCredsRequestMetadata = None,
          issueCredentialData = None,
          issuedCredentialRaw = None,
          issuingDID = issuingDID,
          metaRetries = maxRetries,
          metaNextRetry = Some(Instant.now()),
          metaLastFailure = None,
        )
      )
      count <- credentialRepository
        .create(record) @@ CustomMetricsAspect
        .startRecordingTime(s"${record.id}_issuer_offer_pending_to_sent_ms_gauge")
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
    } yield record
  }

  override def createJWTIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      kidIssuer: Option[KeyId],
      thid: DidCommID,
      credentialSchemaRef: Option[CredentialSchemaRef],
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID,
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
      domain: String
  ): URIO[WalletAccessContext, IssueCredentialRecord] = {
    for {
      _ <- validateClaimsAgainstSchemaIfAny(claims, credentialSchemaRef.map(List(_)))
      attributes <- CredentialService.convertJsonClaimsToAttributes(claims)
      offer <- createDidCommOfferCredential(
        pairwiseIssuerDID = pairwiseIssuerDID,
        pairwiseHolderDID = pairwiseHolderDID,
        credentialSchemaRef = credentialSchemaRef.map(List(_)),
        claims = attributes,
        thid = thid,
        UUID.randomUUID().toString,
        domain,
        IssueCredentialOfferFormat.JWT
      )
      record <- createIssueCredentialRecord(
        pairwiseIssuerDID = pairwiseIssuerDID,
        kidIssuer = kidIssuer,
        thid = thid,
        schemaUris = credentialSchemaRef.map(ref => List(ref.id)),
        validityPeriod = validityPeriod,
        automaticIssuance = automaticIssuance,
        issuingDID = Some(issuingDID),
        credentialFormat = CredentialFormat.JWT,
        offer = offer,
        credentialDefinitionGUID = None,
        credentialDefinitionId = None,
        connectionId = connectionId,
        goalCode = goalCode,
        goal = goal,
        expirationDuration = expirationDuration,
      )
    } yield record
  }

  override def createSDJWTIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      kidIssuer: Option[KeyId],
      thid: DidCommID,
      credentialSchemaRef: Option[CredentialSchemaRef],
      claims: Json,
      validityPeriod: Option[Double] = None,
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID,
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
      domain: String
  ): URIO[WalletAccessContext, IssueCredentialRecord] = {
    val maybeSchemaIds = credentialSchemaRef.map(ref => List(ref.id))
    for {
      _ <- validateClaimsAgainstSchemaIfAny(claims, credentialSchemaRef.map(List(_)))
      attributes <- CredentialService.convertJsonClaimsToAttributes(claims).orDieAsUnmanagedFailure
      offer <- createDidCommOfferCredential(
        pairwiseIssuerDID = pairwiseIssuerDID,
        pairwiseHolderDID = pairwiseHolderDID,
        credentialSchemaRef = credentialSchemaRef.map(List(_)),
        claims = attributes,
        thid = thid,
        UUID.randomUUID().toString,
        domain,
        IssueCredentialOfferFormat.SDJWT
      )
      record <- createIssueCredentialRecord(
        pairwiseIssuerDID = pairwiseIssuerDID,
        kidIssuer = kidIssuer,
        thid = thid,
        schemaUris = maybeSchemaIds,
        validityPeriod = validityPeriod,
        automaticIssuance = automaticIssuance,
        issuingDID = Some(issuingDID),
        credentialFormat = CredentialFormat.SDJWT,
        offer = offer,
        credentialDefinitionGUID = None,
        credentialDefinitionId = None,
        connectionId = connectionId,
        goalCode = goalCode,
        goal = goal,
        expirationDuration = expirationDuration,
      )
    } yield record
  }

  override def createAnonCredsIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      thid: DidCommID,
      credentialDefinitionGUID: UUID,
      credentialDefinitionId: String,
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      goalCode: Option[String],
      goal: Option[String],
      expirationDuration: Option[Duration],
      connectionId: Option[UUID],
  ): URIO[WalletAccessContext, IssueCredentialRecord] = {
    for {
      credentialDefinition <- getCredentialDefinition(credentialDefinitionGUID)
      _ <- CredentialSchema
        .validateAnonCredsClaims(
          credentialDefinition.schemaId,
          claims.toJson,
          uriResolver,
        )
        .orDieAsUnmanagedFailure
      attributes <- CredentialService.convertJsonClaimsToAttributes(claims)
      offer <- createAnonCredsDidCommOfferCredential(
        pairwiseIssuerDID = pairwiseIssuerDID,
        pairwiseHolderDID = pairwiseHolderDID,
        schemaUri = credentialDefinition.schemaId,
        credentialDefinitionGUID = credentialDefinitionGUID,
        credentialDefinitionId = credentialDefinitionId,
        claims = attributes,
        thid = thid,
      )
      schemaUris <- UriString
        .make(credentialDefinition.schemaId)
        .toZIO
        .orDieWith(error => RuntimeException(s"The schemaIs is not a valid URI: $error"))
        .map(uri => Option(List(uri)))
      record <- createIssueCredentialRecord(
        pairwiseIssuerDID = pairwiseIssuerDID,
        kidIssuer = None,
        thid = thid,
        schemaUris = schemaUris,
        validityPeriod = validityPeriod,
        automaticIssuance = automaticIssuance,
        issuingDID = None,
        credentialFormat = CredentialFormat.AnonCreds,
        offer = offer,
        credentialDefinitionGUID = Some(credentialDefinitionGUID),
        credentialDefinitionId = Some(credentialDefinitionId),
        connectionId = connectionId,
        goalCode = goalCode,
        goal = goal,
        expirationDuration = expirationDuration,
      )
    } yield record
  }

  override def getIssueCredentialRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): URIO[WalletAccessContext, Seq[IssueCredentialRecord]] =
    credentialRepository.findByStates(ignoreWithZeroRetries, limit, states*)

  override def getIssueCredentialRecordsByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): UIO[Seq[IssueCredentialRecord]] =
    credentialRepository.findByStatesForAllWallets(ignoreWithZeroRetries, limit, states*)

  override def receiveCredentialOffer(
      offer: OfferCredential
  ): ZIO[WalletAccessContext, InvalidCredentialOffer, IssueCredentialRecord] = {
    for {
      attachment <- ZIO
        .fromOption(offer.attachments.headOption)
        .mapError(_ => InvalidCredentialOffer("No attachment found"))

      format <- ZIO
        .fromOption(attachment.format)
        .mapError(_ => InvalidCredentialOffer("No attachment format found"))

      credentialFormat <- format match
        case value if value == IssueCredentialOfferFormat.JWT.name      => ZIO.succeed(CredentialFormat.JWT)
        case value if value == IssueCredentialOfferFormat.SDJWT.name    => ZIO.succeed(CredentialFormat.SDJWT)
        case value if value == IssueCredentialOfferFormat.Anoncred.name => ZIO.succeed(CredentialFormat.AnonCreds)
        case value => ZIO.fail(InvalidCredentialOffer(s"Unsupported credential format: $value"))

      _ <- validateCredentialOfferAttachment(credentialFormat, attachment)
      record <- ZIO.succeed(
        IssueCredentialRecord(
          id = DidCommID(),
          createdAt = Instant.now,
          updatedAt = None,
          thid = DidCommID(offer.thid.getOrElse(offer.id)),
          schemaUris = None,
          credentialDefinitionId = None,
          credentialDefinitionUri = None,
          credentialFormat = credentialFormat,
          invitation = None,
          role = Role.Holder,
          subjectId = None,
          keyId = None,
          validityPeriod = None,
          automaticIssuance = None,
          protocolState = IssueCredentialRecord.ProtocolState.OfferReceived,
          offerCredentialData = Some(offer),
          requestCredentialData = None,
          anonCredsRequestMetadata = None,
          issueCredentialData = None,
          issuedCredentialRaw = None,
          issuingDID = None,
          metaRetries = maxRetries,
          metaNextRetry = Some(Instant.now()),
          metaLastFailure = None,
        )
      )
      count <- credentialRepository.create(record)
    } yield record
  }

  private def validateCredentialOfferAttachment(
      credentialFormat: CredentialFormat,
      attachment: AttachmentDescriptor
  ): IO[InvalidCredentialOffer, Unit] = for {
    _ <- credentialFormat match
      case CredentialFormat.JWT | CredentialFormat.SDJWT =>
        attachment.data match
          case JsonData(json) =>
            ZIO
              .fromEither(json.as[CredentialOfferAttachment])
              .mapError(err => InvalidCredentialOffer(s"An error occurred when parsing the offer attachment: $err"))
          case _ =>
            ZIO.fail(InvalidCredentialOffer(s"Only JSON attachments are supported in JWT offers"))
      case CredentialFormat.AnonCreds =>
        attachment.data match
          case Base64(value) =>
            for {
              _ <- ZIO
                .attempt(AnoncredCredentialOffer(value))
                .mapError(e =>
                  InvalidCredentialOffer(s"An error occurred when parsing the offer attachment: ${e.toString}")
                )
            } yield ()
          case _ =>
            ZIO.fail(InvalidCredentialOffer(s"Only Base64 attachments are supported in AnonCreds offers"))
  } yield ()

  private[this] def validatePrismDID(
      did: String
  ): IO[UnsupportedDidFormat, PrismDID] = ZIO
    .fromEither(PrismDID.fromString(did))
    .mapError(_ => UnsupportedDidFormat(did))

  // TODO: Refactor this method in order to use more strict signatures
  private[this] def validateClaimsAgainstSchemaIfAny(
      claims: Json,
      maybeSchemaIds: Option[List[CredentialSchemaRef]]
  ): UIO[Unit] = maybeSchemaIds match
    case Some(schemaIds) =>
      for {
        _ <- ZIO
          .collectAll(
            schemaIds
              .map(_.id)
              .map(schemaId =>
                CredentialSchema
                  .validateJWTCredentialSubject(schemaId, claims.toJson, uriResolver)
              )
          )
          .orDieAsUnmanagedFailure
      } yield ZIO.unit
    case None =>
      ZIO.unit

  private[this] def getCredentialDefinition(
      guid: UUID
  ): UIO[CredentialDefinition] = credentialDefinitionService
    .getByGUID(guid)
    .orDieAsUnmanagedFailure

  private[this] def getCredentialDefinitionPrivatePart(
      guid: UUID
  ): URIO[WalletAccessContext, CredentialDefinitionSecret] = for {
    maybeCredentialDefinitionSecret <- genericSecretStorage
      .get[UUID, CredentialDefinitionSecret](guid)
      .orDie
    credentialDefinitionSecret <- ZIO
      .fromOption(maybeCredentialDefinitionSecret)
      .mapError(_ => CredentialDefinitionPrivatePartNotFound(guid))
      .orDieAsUnmanagedFailure
  } yield credentialDefinitionSecret

  override def acceptCredentialOffer(
      recordId: DidCommID,
      maybeSubjectId: Option[String],
      keyId: Option[KeyId]
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.OfferReceived)
      count <- (record.credentialFormat, maybeSubjectId) match
        case (CredentialFormat.JWT | CredentialFormat.SDJWT, Some(subjectId)) =>
          for {
            _ <- validatePrismDID(subjectId)
            count <- credentialRepository
              .updateWithSubjectId(recordId, subjectId, keyId, ProtocolState.RequestPending)
              @@ CustomMetricsAspect.startRecordingTime(
                s"${record.id}_issuance_flow_holder_req_pending_to_generated"
              )
          } yield count
        case (CredentialFormat.AnonCreds, None) =>
          credentialRepository
            .updateProtocolState(recordId, ProtocolState.OfferReceived, ProtocolState.RequestPending)
            @@ CustomMetricsAspect.startRecordingTime(
              s"${record.id}_issuance_flow_holder_req_pending_to_generated"
            )
        case (format, maybeSubjectId) =>
          ZIO.dieMessage(s"Invalid subjectId input for $format offer acceptance: $maybeSubjectId")
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  private def createPresentationPayload(
      record: IssueCredentialRecord,
      subject: JwtIssuer
  ): URIO[WalletAccessContext, PresentationPayload] = {
    for {
      maybeOptions <- getOptionsFromOfferCredentialData(record)
    } yield {
      W3cPresentationPayload(
        `@context` = Vector("https://www.w3.org/2018/presentations/v1"),
        maybeId = None,
        `type` = Vector("VerifiablePresentation"),
        verifiableCredential = IndexedSeq.empty,
        holder = subject.did.toString,
        verifier = IndexedSeq.empty ++ maybeOptions.map(_.domain),
        maybeIssuanceDate = None,
        maybeExpirationDate = None
      ).toJwtPresentationPayload.copy(maybeNonce = maybeOptions.map(_.challenge))
    }
  }

  private def getLongForm(
      did: PrismDID,
      allowUnpublishedIssuingDID: Boolean = false
  ): URIO[WalletAccessContext, LongFormPrismDID] = {
    for {
      maybeDidState <- managedDIDService
        .getManagedDIDState(did.asCanonical)
        .orDieWith(e => RuntimeException(s"Error occurred while getting DID from wallet: ${e.toString}"))
      didState <- ZIO
        .fromOption(maybeDidState)
        .mapError(_ => DIDNotFoundInWallet(did))
        .orDieAsUnmanagedFailure
      _ <- (didState match
        case s @ ManagedDIDState(_, _, PublicationState.Published(_)) => ZIO.succeed(s)
        case s => ZIO.cond(allowUnpublishedIssuingDID, s, DIDNotPublished(did, s.publicationState))
      ).orDieAsUnmanagedFailure
      longFormPrismDID = PrismDID.buildLongFormFromOperation(didState.createOperation)
    } yield longFormPrismDID
  }

  private[this] def getKeyId(
      did: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: Option[KeyId]
  ): UIO[PublicKey] = {
    for {
      maybeDidData <- didService
        .resolveDID(did)
        .orDieWith(e => RuntimeException(s"Error occurred while resolving the DID: ${e.toString}"))
      didData <- ZIO
        .fromOption(maybeDidData)
        .mapError(_ => DIDNotResolved(did))
        .orDieAsUnmanagedFailure
      matchingKeys = didData._2.publicKeys.filter(pk => pk.purpose == verificationRelationship)
      result <- (matchingKeys, keyId) match {
        case (Seq(), _) =>
          ZIO.fail(KeyNotFoundInDID(did, verificationRelationship)).orDieAsUnmanagedFailure
        case (Seq(singleKey), None) =>
          ZIO.succeed(singleKey)
        case (multipleKeys, Some(kid)) =>
          ZIO
            .fromOption(multipleKeys.find(_.id.value.endsWith(kid.value)))
            .mapError(_ => KeyNotFoundInDID(did, verificationRelationship))
            .orDieAsUnmanagedFailure
        case (multipleKeys, None) =>
          ZIO
            .fail(
              MultipleKeysWithSamePurposeFoundInDID(did, verificationRelationship)
            )
            .orDieAsUnmanagedFailure
      }
    } yield result
  }

  override def getJwtIssuer(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: Option[KeyId] = None
  ): URIO[WalletAccessContext, JwtIssuer] = {
    for {
      issuingPublicKey <- getKeyId(jwtIssuerDID, verificationRelationship, keyId)
      jwtIssuer <- managedDIDService
        .findDIDKeyPair(jwtIssuerDID.asCanonical, issuingPublicKey.id)
        .flatMap {
          case Some(keyPair: Secp256k1KeyPair) => {
            val jwtIssuer = JwtIssuer(
              jwtIssuerDID.did,
              ES256KSigner(keyPair.privateKey.toJavaPrivateKey, keyId),
              keyPair.publicKey.toJavaPublicKey
            )
            ZIO.some(jwtIssuer)
          }
          case Some(keyPair: Ed25519KeyPair) => {
            val jwtIssuer = JwtIssuer(
              jwtIssuerDID.did,
              EdSigner(keyPair, keyId),
              keyPair.publicKey.toJava
            )
            ZIO.some(jwtIssuer)
          }
          case _ => ZIO.none
        }
        .someOrFail(
          KeyPairNotFoundInWallet(jwtIssuerDID, issuingPublicKey.id, issuingPublicKey.publicKeyData.crv.name)
        )
        .orDieAsUnmanagedFailure
    } yield jwtIssuer
  }

  private def getEd25519SigningKeyPair(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: Option[KeyId] = None
  ): URIO[WalletAccessContext, Ed25519KeyPair] = {
    for {
      issuingPublicKey <- getKeyId(jwtIssuerDID, verificationRelationship, keyId)
      ed25519keyPair <- managedDIDService
        .findDIDKeyPair(jwtIssuerDID.asCanonical, issuingPublicKey.id)
        .map(_.collect { case keyPair: Ed25519KeyPair => keyPair })
        .someOrFail(KeyPairNotFoundInWallet(jwtIssuerDID, issuingPublicKey.id, issuingPublicKey.publicKeyData.crv.name))
        .orDieAsUnmanagedFailure
    } yield ed25519keyPair
  }

  /** @param jwtIssuerDID
    *   This can holder prism did / issuer prism did
    * @param verificationRelationship
    *   Holder it Authentication and Issuer it is AssertionMethod
    * @param keyId
    *   Optional KID parameter in case of DID has multiple keys with same purpose
    * @return
    *   JwtIssuer
    * @see
    *   org.hyperledger.identus.pollux.vc.jwt.Issuer
    */
  private def getSDJwtIssuer(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: Option[KeyId]
  ): URIO[WalletAccessContext, JwtIssuer] = {
    for {
      ed25519keyPair <- getEd25519SigningKeyPair(jwtIssuerDID, verificationRelationship, keyId)
    } yield {
      JwtIssuer(
        jwtIssuerDID.did,
        EdSigner(ed25519keyPair, keyId),
        ed25519keyPair.publicKey.toJava
      )
    }
  }

  private[this] def generateCredentialRequest(
      recordId: DidCommID,
      getIssuer: (
          did: LongFormPrismDID,
          verificationRelation: VerificationRelationship,
          keyId: Option[KeyId]
      ) => URIO[WalletAccessContext, JwtIssuer]
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.RequestPending)
      subjectId <- ZIO
        .fromOption(record.subjectId)
        .orDieWith(_ => RuntimeException(s"No 'subjectId' found in record: ${recordId.value}"))
      formatAndOffer <- ZIO
        .fromOption(record.offerCredentialFormatAndData)
        .orDieWith(_ => RuntimeException(s"No 'offer' found in record: ${recordId.value}"))
      subjectDID <- validatePrismDID(subjectId)
      longFormPrismDID <- getLongForm(subjectDID, true)
      jwtIssuer <- getIssuer(longFormPrismDID, VerificationRelationship.Authentication, record.keyId)
      presentationPayload <- createPresentationPayload(record, jwtIssuer)
      signedPayload = JwtPresentation.encodeJwt(presentationPayload.toJwtPresentationPayload, jwtIssuer)
      request = createDidCommRequestCredential(formatAndOffer._1, formatAndOffer._2, signedPayload)
      count <- credentialRepository
        .updateWithJWTRequestCredential(recordId, request, ProtocolState.RequestGenerated)
        @@ CustomMetricsAspect.endRecordingTime(
          s"${record.id}_issuance_flow_holder_req_pending_to_generated",
          "issuance_flow_holder_req_pending_to_generated_ms_gauge"
        ) @@ CustomMetricsAspect.startRecordingTime(s"${record.id}_issuance_flow_holder_req_generated_to_sent")
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  override def generateJWTCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
    generateCredentialRequest(recordId, getJwtIssuer)

  override def generateSDJWTCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound | UnsupportedDidFormat, IssueCredentialRecord] =
    generateCredentialRequest(recordId, getSDJwtIssuer)

  override def generateAnonCredsCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.RequestPending)
      offerCredential <- ZIO
        .fromOption(record.offerCredentialData)
        .orDieWith(_ => RuntimeException(s"No 'offer' found in record: ${recordId.value}"))
      body = RequestCredential.Body(goal_code = Some("Request Credential"))
      createCredentialRequest <- createAnonCredsRequestCredential(offerCredential)
      attachments = Seq(
        AttachmentDescriptor.buildBase64Attachment(
          mediaType = Some("application/json"),
          format = Some(IssueCredentialRequestFormat.Anoncred.name),
          payload = createCredentialRequest.request.data.getBytes()
        )
      )
      requestMetadata = createCredentialRequest.metadata
      request = RequestCredential(
        body = body,
        attachments = attachments,
        from =
          offerCredential.to.getOrElse(throw new IllegalArgumentException("OfferCredential must have a recipient")),
        to = offerCredential.from,
        thid = offerCredential.thid
      )
      count <- credentialRepository
        .updateWithAnonCredsRequestCredential(recordId, request, requestMetadata, ProtocolState.RequestGenerated)
        @@ CustomMetricsAspect.endRecordingTime(
          s"${record.id}_issuance_flow_holder_req_pending_to_generated",
          "issuance_flow_holder_req_pending_to_generated_ms_gauge"
        ) @@ CustomMetricsAspect.startRecordingTime(s"${record.id}_issuance_flow_holder_req_generated_to_sent")
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  private def createAnonCredsRequestCredential(
      offerCredential: OfferCredential
  ): URIO[WalletAccessContext, AnoncredCreateCrendentialRequest] = {
    for {
      attachmentData <- ZIO
        .fromOption(
          offerCredential.attachments
            .find(_.format.contains(IssueCredentialOfferFormat.Anoncred.name))
            .map(_.data)
            .flatMap {
              case Base64(value) => Some(new String(java.util.Base64.getUrlDecoder.decode(value)))
              case _             => None
            }
        )
        .orDieWith(_ => RuntimeException(s"No AnonCreds attachment found in the offer"))
      credentialOffer = anoncreds.AnoncredCredentialOffer(attachmentData)
      credDefContent <- uriResolver
        .resolve(credentialOffer.getCredDefId)
        .orDieAsUnmanagedFailure
      credentialDefinition = anoncreds.AnoncredCredentialDefinition(credDefContent)
      linkSecret <- linkSecretService.fetchOrCreate()
      createCredentialRequest = AnoncredLib.createCredentialRequest(linkSecret, credentialDefinition, credentialOffer)
    } yield createCredentialRequest
  }

  override def receiveCredentialRequest(
      request: RequestCredential
  ): ZIO[WalletAccessContext, InvalidCredentialRequest | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] = {
    for {
      thid <- ZIO
        .fromOption(request.thid.map(DidCommID(_)))
        .mapError(_ => InvalidCredentialRequest("No 'thid' found"))
      record <- getRecordWithThreadIdAndStates(
        thid,
        ignoreWithZeroRetries = true,
        ProtocolState.InvitationGenerated,
        ProtocolState.OfferPending,
        ProtocolState.OfferSent
      )
      _ <- credentialRepository.updateWithJWTRequestCredential(record.id, request, ProtocolState.RequestReceived)
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  override def acceptCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.RequestReceived)
      request <- ZIO
        .fromOption(record.requestCredentialData)
        .orDieWith(_ => RuntimeException(s"No 'requestCredentialData' found in record: ${recordId.value}"))
      issue = createDidCommIssueCredential(request)
      count <- credentialRepository
        .updateWithIssueCredential(recordId, issue, ProtocolState.CredentialPending)
        @@ CustomMetricsAspect.startRecordingTime(
          s"${record.id}_issuance_flow_issuer_credential_pending_to_generated"
        )
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  override def receiveCredentialIssue(
      issueCredential: IssueCredential
  ): ZIO[WalletAccessContext, InvalidCredentialIssue | RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] =
    for {
      thid <- ZIO
        .fromOption(issueCredential.thid.map(DidCommID(_)))
        .mapError(_ => InvalidCredentialIssue("No 'thid' found"))
      record <- getRecordWithThreadIdAndStates(
        thid,
        ignoreWithZeroRetries = true,
        ProtocolState.RequestPending,
        ProtocolState.RequestSent
      )
      attachment <- ZIO
        .fromOption(issueCredential.attachments.headOption)
        .mapError(_ => InvalidCredentialIssue("No attachment found"))

      _ <- {
        val result = attachment match {
          case AttachmentDescriptor(
                id,
                media_type,
                Base64(v),
                Some(IssueCredentialIssuedFormat.Anoncred.name),
                _,
                _,
                _,
                _
              ) =>
            for {
              processedCredential <- processAnonCredsCredential(record, java.util.Base64.getUrlDecoder.decode(v))
              attachment = AttachmentDescriptor.buildBase64Attachment(
                id = id,
                mediaType = media_type,
                format = Some(IssueCredentialIssuedFormat.Anoncred.name),
                payload = processedCredential.data.getBytes
              )
              processedIssuedCredential = issueCredential.copy(attachments = Seq(attachment))
              result <-
                updateWithCredential(
                  processedIssuedCredential,
                  record,
                  attachment,
                  Some(List(processedCredential.getSchemaId)),
                  Some(processedCredential.getCredDefId)
                )
            } yield result
          case attachment =>
            updateWithCredential(issueCredential, record, attachment, None, None)
        }
        result
      }
      record <- credentialRepository.getById(record.id)
    } yield record

  private def updateWithCredential(
      issueCredential: IssueCredential,
      record: IssueCredentialRecord,
      attachment: AttachmentDescriptor,
      schemaId: Option[List[String]],
      credDefId: Option[String]
  ) = {
    credentialRepository
      .updateWithIssuedRawCredential(
        record.id,
        issueCredential,
        attachment.data.toJson,
        schemaId,
        credDefId,
        ProtocolState.CredentialReceived
      )
  }

  private def processAnonCredsCredential(
      record: IssueCredentialRecord,
      credentialBytes: Array[Byte]
  ): URIO[WalletAccessContext, anoncreds.AnoncredCredential] = {
    for {
      credential <- ZIO.succeed(anoncreds.AnoncredCredential(new String(credentialBytes)))
      credDefContent <- uriResolver
        .resolve(credential.getCredDefId)
        .orDieAsUnmanagedFailure
      credentialDefinition = anoncreds.AnoncredCredentialDefinition(credDefContent)
      metadata <- ZIO
        .fromOption(record.anonCredsRequestMetadata)
        .orDieWith(_ => RuntimeException(s"No AnonCreds request metadata found in record: ${record.id.value}"))
      linkSecret <- linkSecretService.fetchOrCreate()
      credential <- ZIO
        .attempt(
          AnoncredLib.processCredential(
            anoncreds.AnoncredCredential(new String(credentialBytes)),
            metadata,
            linkSecret,
            credentialDefinition
          )
        )
        .orDieWith(error => RuntimeException(s"AnonCreds credential processing error: ${error.getMessage}"))
    } yield credential
  }

  override def markOfferSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    updateCredentialRecordProtocolState(
      recordId,
      IssueCredentialRecord.ProtocolState.OfferPending,
      IssueCredentialRecord.ProtocolState.OfferSent
    )

  override def markCredentialOfferInvitationExpired(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    updateCredentialRecordProtocolState(
      recordId,
      IssueCredentialRecord.ProtocolState.RequestReceived,
      IssueCredentialRecord.ProtocolState.InvitationExpired
    )
  override def markRequestSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    updateCredentialRecordProtocolState(
      recordId,
      IssueCredentialRecord.ProtocolState.RequestGenerated,
      IssueCredentialRecord.ProtocolState.RequestSent
    ) @@ CustomMetricsAspect.endRecordingTime(
      s"${recordId}_issuance_flow_holder_req_generated_to_sent",
      "issuance_flow_holder_req_generated_to_sent_ms_gauge"
    )

  private def markCredentialGenerated(
      record: IssueCredentialRecord,
      issueCredential: IssueCredential
  ): URIO[WalletAccessContext, IssueCredentialRecord] = {
    for {
      count <- credentialRepository
        .updateWithIssueCredential(record.id, issueCredential, IssueCredentialRecord.ProtocolState.CredentialGenerated)
        @@ CustomMetricsAspect.endRecordingTime(
          s"${record.id}_issuance_flow_issuer_credential_pending_to_generated",
          "issuance_flow_issuer_credential_pending_to_generated_ms_gauge"
        ) @@ CustomMetricsAspect.startRecordingTime(s"${record.id}_issuance_flow_issuer_credential_generated_to_sent")
      walletAccessContext <- ZIO.service[WalletAccessContext]
      _ <- messageProducer
        .produce(TOPIC_NAME, record.id.uuid, WalletIdAndRecordId(walletAccessContext.walletId.toUUID, record.id.uuid))
        .orDie
      record <- credentialRepository.getById(record.id)
    } yield record
  }

  override def markCredentialSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] =
    updateCredentialRecordProtocolState(
      recordId,
      IssueCredentialRecord.ProtocolState.CredentialGenerated,
      IssueCredentialRecord.ProtocolState.CredentialSent
    ) @@ CustomMetricsAspect.endRecordingTime(
      s"${recordId}_issuance_flow_issuer_credential_generated_to_sent",
      "issuance_flow_issuer_credential_generated_to_sent_ms_gauge"
    )

  override def reportProcessingFailure(
      recordId: DidCommID,
      failReason: Option[Failure]
  ): URIO[WalletAccessContext, Unit] =
    credentialRepository.updateAfterFail(recordId, failReason)

  private def getRecordWithState(
      recordId: DidCommID,
      state: ProtocolState
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] = {
    for {
      record <- credentialRepository.getById(recordId)
      _ <- record.protocolState match {
        case s if s == state => ZIO.unit
        case s               => ZIO.fail(RecordNotFound(recordId, Some(s)))
      }
    } yield record
  }

  private def getRecordWithThreadIdAndStates(
      thid: DidCommID,
      ignoreWithZeroRetries: Boolean,
      states: ProtocolState*
  ): ZIO[WalletAccessContext, RecordNotFoundForThreadIdAndStates, IssueCredentialRecord] = {
    for {
      record <- credentialRepository
        .findByThreadId(thid, ignoreWithZeroRetries)
        .someOrFail(RecordNotFoundForThreadIdAndStates(thid, states*))
      _ <- record.protocolState match {
        case s if states.contains(s) => ZIO.unit
        case state                   => ZIO.fail(RecordNotFoundForThreadIdAndStates(thid, states*))
      }
    } yield record
  }

  private def createDidCommOfferCredential(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      credentialSchemaRef: Option[List[CredentialSchemaRef]],
      claims: Seq[Attribute],
      thid: DidCommID,
      challenge: String,
      domain: String,
      offerFormat: IssueCredentialOfferFormat
  ): UIO[OfferCredential] = {
    val maybeSchemaIds = credentialSchemaRef.map(_.map(_.id.toString))
    for {
      credentialPreview <- ZIO.succeed(CredentialPreview(schema_ids = maybeSchemaIds, attributes = claims))
      body = OfferCredential.Body(
        goal_code = Some("Offer Credential"),
        credential_preview = credentialPreview,
      )
      attachments <- ZIO.succeed(
        Seq(
          AttachmentDescriptor.buildJsonAttachment(
            mediaType = Some("application/json"),
            format = Some(offerFormat.name),
            payload = PresentationAttachment(
              Some(Options(challenge, domain)),
              PresentationDefinition(format = Some(ClaimFormat(jwt = Some(Jwt(alg = Seq("ES256K"))))))
            )
          )
        )
      )
    } yield OfferCredential(
      body = body,
      attachments = attachments,
      from = pairwiseIssuerDID,
      to = pairwiseHolderDID,
      thid = Some(thid.value)
    )
  }

  private def createAnonCredsDidCommOfferCredential(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: Option[DidId],
      schemaUri: String,
      credentialDefinitionGUID: UUID,
      credentialDefinitionId: String,
      claims: Seq[Attribute],
      thid: DidCommID
  ): URIO[WalletAccessContext, OfferCredential] = {
    for {
      credentialPreview <- ZIO.succeed(CredentialPreview(schema_ids = Some(List(schemaUri)), attributes = claims))
      body = OfferCredential.Body(
        goal_code = Some("Offer Credential"),
        credential_preview = credentialPreview,
      )
      attachments <- createAnonCredsCredentialOffer(credentialDefinitionGUID, credentialDefinitionId).map { offer =>
        Seq(
          AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(IssueCredentialOfferFormat.Anoncred.name),
            payload = offer.data.getBytes()
          )
        )
      }
    } yield OfferCredential(
      body = body,
      attachments = attachments,
      from = pairwiseIssuerDID,
      to = pairwiseHolderDID,
      thid = Some(thid.value)
    )
  }

  private def createAnonCredsCredentialOffer(
      credentialDefinitionGUID: UUID,
      credentialDefinitionId: String
  ): URIO[WalletAccessContext, AnoncredCredentialOffer] =
    for {
      credentialDefinition <- getCredentialDefinition(credentialDefinitionGUID)
      cd = anoncreds.AnoncredCredentialDefinition(credentialDefinition.definition.toString)
      kcp = anoncreds.AnoncredCredentialKeyCorrectnessProof(credentialDefinition.keyCorrectnessProof.toString)
      credentialDefinitionSecret <- getCredentialDefinitionPrivatePart(credentialDefinition.guid)
      cdp = anoncreds.AnoncredCredentialDefinitionPrivate(credentialDefinitionSecret.json.toString)
      createCredentialDefinition = AnoncredCreateCredentialDefinition(cd, cdp, kcp)
      offer = AnoncredLib.createOffer(createCredentialDefinition, credentialDefinitionId)
    } yield offer

  private[this] def createDidCommRequestCredential(
      format: IssueCredentialOfferFormat,
      offer: OfferCredential,
      signedPresentation: JWT
  ): RequestCredential = {
    RequestCredential(
      body = RequestCredential.Body(
        goal_code = offer.body.goal_code,
        comment = offer.body.comment,
      ),
      attachments = Seq(
        AttachmentDescriptor
          .buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(format.name),
            // FIXME copy payload will probably not work for anoncreds!
            payload = signedPresentation.value.getBytes(),
          )
      ),
      thid = offer.thid.orElse(Some(offer.id)),
      from = offer.to.getOrElse(throw new IllegalArgumentException("OfferCredential must have a recipient")),
      to = offer.from
    )
  }

  private def createDidCommIssueCredential(request: RequestCredential): IssueCredential = {
    IssueCredential(
      body = IssueCredential.Body(
        goal_code = request.body.goal_code,
        comment = request.body.comment,
        replacement_id = None,
        more_available = None,
      ),
      attachments = Seq(), // FIXME !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      thid = request.thid.orElse(Some(request.id)),
      from = request.to,
      to = request.from
    )
  }

  /** this is an auxiliary function.
    *
    * @note
    *   Between updating and getting the CredentialRecord back the CredentialRecord can be updated by other operations
    *   in the middle.
    *
    * TODO: this should be improved to behave exactly like atomic operation.
    */
  private def updateCredentialRecordProtocolState(
      id: DidCommID,
      from: IssueCredentialRecord.ProtocolState,
      to: IssueCredentialRecord.ProtocolState
  ): ZIO[WalletAccessContext, InvalidStateForOperation, IssueCredentialRecord] = {
    for {
      record <- credentialRepository.getById(id)
      updatedRecord <- record.protocolState match
        case currentState if currentState == to => ZIO.succeed(record) // Idempotent behaviour
        case currentState if currentState == from =>
          credentialRepository.updateProtocolState(id, from, to) *> credentialRepository.getById(id)
        case _ => ZIO.fail(InvalidStateForOperation(record.protocolState))
    } yield updatedRecord
  }

  override def generateJWTCredential(
      recordId: DidCommID,
      statusListRegistryUrl: String,
  ): ZIO[WalletAccessContext, RecordNotFound | CredentialRequestValidationFailed, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.CredentialPending)
      issuingDID <- ZIO
        .fromOption(record.issuingDID)
        .orElse(ZIO.dieMessage(s"Issuing DID not found in record: ${recordId.value}"))
      issue <- ZIO
        .fromOption(record.issueCredentialData)
        .orElse(ZIO.dieMessage(s"Issue credential data not found in record: ${recordId.value}"))
      longFormPrismDID <- getLongForm(issuingDID, true)
      maybeOfferOptions <- getOptionsFromOfferCredentialData(record)
      requestJwt <- getJwtFromRequestCredentialData(record)
      offerCredentialData <- ZIO
        .fromOption(record.offerCredentialData)
        .orElse(ZIO.dieMessage(s"Offer credential data not found in record: ${recordId.value}"))
      preview = offerCredentialData.body.credential_preview
      claims <- CredentialService.convertAttributesToJsonClaims(preview.body.attributes).orDieAsUnmanagedFailure
      jwtIssuer <- getJwtIssuer(longFormPrismDID, VerificationRelationship.AssertionMethod, record.keyId)
      jwtPresentation <- validateRequestCredentialDataProof(maybeOfferOptions, requestJwt)
        .tapError(error =>
          credentialRepository
            .updateProtocolState(record.id, ProtocolState.CredentialPending, ProtocolState.ProblemReportPending)
        )
        .orDieAsUnmanagedFailure

      // Custom for JWT
      issuanceDate = Instant.now()
      credentialStatus <- allocateNewCredentialInStatusListForWallet(record, statusListRegistryUrl, jwtIssuer)
      // TODO: get schema when schema registry is available if schema ID is provided
      w3Credential = W3cCredentialPayload(
        `@context` = Set(
          "https://www.w3.org/2018/credentials/v1"
        ), // TODO: his information should come from Schema registry by record.schemaId
        maybeId = None,
        `type` =
          Set("VerifiableCredential"), // TODO: This information should come from Schema registry by record.schemaId
        issuer = CredentialIssuer(issuingDID.did.toString, `type` = "Profile"),
        issuanceDate = issuanceDate,
        maybeExpirationDate = record.validityPeriod.map(sec => issuanceDate.plusSeconds(sec.toLong)),
        maybeCredentialSchema = record.schemaUris.map(ids =>
          ids.map(id => org.hyperledger.identus.pollux.vc.jwt.CredentialSchema(id, VC_JSON_SCHEMA_TYPE))
        ),
        maybeCredentialStatus = Some(credentialStatus),
        credentialSubject = claims.add("id", Json.Str(jwtPresentation.iss)),
        maybeRefreshService = None,
        maybeEvidence = None,
        maybeTermsOfUse = None,
        maybeValidFrom = None,
        maybeValidUntil = None
      )
      signedJwtCredential = W3CCredential.toEncodedJwt(w3Credential, jwtIssuer)
      issueCredential = IssueCredential.build(
        fromDID = issue.from,
        toDID = issue.to,
        thid = issue.thid,
        credentials = Seq(IssueCredentialIssuedFormat.JWT -> signedJwtCredential.value.getBytes)
      )
      // End custom

      record <- markCredentialGenerated(record, issueCredential)
    } yield record
  }

  override def generateSDJWTCredential(
      recordId: DidCommID,
      expirationTime: Duration,
  ): ZIO[
    WalletAccessContext,
    RecordNotFound | ExpirationDateHasPassed | VCJwtHeaderParsingError,
    IssueCredentialRecord
  ] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.CredentialPending)
      issuingDID <- ZIO
        .fromOption(record.issuingDID)
        .orElse(ZIO.dieMessage(s"Issuing DID not found in record: ${recordId.value}"))
      issue <- ZIO
        .fromOption(record.issueCredentialData)
        .orElse(ZIO.dieMessage(s"Issue credential data not found in record: ${recordId.value}"))
      longFormPrismDID <- getLongForm(issuingDID, true)
      maybeOfferOptions <- getOptionsFromOfferCredentialData(record)
      requestJwt <- getJwtFromRequestCredentialData(record)
      offerCredentialData <- ZIO
        .fromOption(record.offerCredentialData)
        .orElse(ZIO.dieMessage(s"Offer credential data not found in record: ${recordId.value}"))
      preview = offerCredentialData.body.credential_preview
      claims <- CredentialService.convertAttributesToJsonClaims(preview.body.attributes).orDieAsUnmanagedFailure
      jwtPresentation <- validateRequestCredentialDataProof(maybeOfferOptions, requestJwt)
        .tapError(error =>
          credentialRepository
            .updateProtocolState(record.id, ProtocolState.CredentialPending, ProtocolState.ProblemReportPending)
        )
        .orDieAsUnmanagedFailure
      jwtHeader <- JWTVerification.extractJwtHeader(requestJwt) match
        case ZValidation.Success(log, header) => ZIO.succeed(header)
        case ZValidation.Failure(log, failure) =>
          ZIO.fail(VCJwtHeaderParsingError(s"Extraction of JwtHeader failed ${failure.toChunk.toString}"))
      ed25519KeyPair <- getEd25519SigningKeyPair(
        longFormPrismDID,
        VerificationRelationship.AssertionMethod,
        record.keyId
      )
      sdJwtPrivateKey = sdjwt.IssuerPrivateKey(ed25519KeyPair.privateKey)
      jsonWebKey <- didResolver.resolve(jwtPresentation.iss) flatMap {
        case failed: DIDResolutionFailed =>
          ZIO.dieMessage(s"Error occurred while resolving the DID: ${failed.error.toString}")
        case succeeded: DIDResolutionSucceeded =>
          jwtHeader.keyId match {
            case Some(
                  kid
                ) => // TODO should we check in authentication and assertion or just in verificationMethod since this cane different how did document is implemented
              ZIO
                .fromOption(succeeded.didDocument.verificationMethod.find(_.id.endsWith(kid)).map(_.publicKeyJwk))
                .orElse(
                  ZIO.dieMessage(
                    s"Required public Key for holder binding is not found in DID document for the kid: $kid"
                  )
                )
            case None =>
              ZIO.succeed(None) // JwtHeader keyId is None, Issued credential is not bound to any holder public key
          }
      }

      now = Instant.now.getEpochSecond
      exp = claims.get("exp").flatMap(_.asNumber).map(_.value.longValue())
      expInSeconds <- ZIO.fromEither(exp match {
        case Some(e) if e > now => Right(e)
        case Some(e)            => Left(ExpirationDateHasPassed(e))
        case _                  => Right(Instant.now.plus(expirationTime).getEpochSecond)
      })
      claimsUpdated = claims
        .add("iss", Json.Str(issuingDID.did.toString)) // This is issuer did
        .add("sub", Json.Str(jwtPresentation.iss)) // This is subject did
        .add("iat", Json.Num(now))
        .add("exp", Json.Num(expInSeconds))
      credential = {
        jsonWebKey match {
          case Some(jwk) =>
            SDJWT.issueCredential(
              sdJwtPrivateKey,
              claimsUpdated.toJson,
              sdjwt.HolderPublicKey.fromJWT(jwk.toJson)
            )
          case None =>
            SDJWT.issueCredential(
              sdJwtPrivateKey,
              claimsUpdated.toJson,
            )
        }
      }
      issueCredential = IssueCredential.build(
        fromDID = issue.from,
        toDID = issue.to,
        thid = issue.thid,
        credentials = Seq(IssueCredentialIssuedFormat.SDJWT -> credential.compact.getBytes)
      )
      record <- markCredentialGenerated(record, issueCredential)
    } yield record

  }

  private def allocateNewCredentialInStatusListForWallet(
      record: IssueCredentialRecord,
      statusListRegistryUrl: String,
      jwtIssuer: JwtIssuer
  ): URIO[WalletAccessContext, CredentialStatus] =
    for {
      cslAndIndex <- credentialStatusListRepository.incrementAndGetStatusListIndex(
        jwtIssuer,
        statusListRegistryUrl
      )
      statusListId = cslAndIndex._1
      indexInStatusList = cslAndIndex._2
      _ <- credentialStatusListRepository.allocateSpaceForCredential(
        issueCredentialRecordId = record.id,
        credentialStatusListId = statusListId,
        statusListIndex = indexInStatusList
      )
    } yield CredentialStatus(
      id = s"$statusListRegistryUrl/credential-status/$statusListId#$indexInStatusList",
      `type` = "StatusList2021Entry",
      statusPurpose = StatusPurpose.Revocation,
      statusListIndex = indexInStatusList,
      statusListCredential = s"$statusListRegistryUrl/credential-status/$statusListId"
    )

  override def generateAnonCredsCredential(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, RecordNotFound, IssueCredentialRecord] = {
    for {
      record <- getRecordWithState(recordId, ProtocolState.CredentialPending)
      requestCredential <- ZIO
        .fromOption(record.requestCredentialData)
        .orElse(ZIO.dieMessage(s"No request credential data found in record: ${record.id}"))
      body = IssueCredential.Body(goal_code = Some("Issue Credential"))
      attachments <- createAnonCredsCredential(record).map { credential =>
        Seq(
          AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(IssueCredentialIssuedFormat.Anoncred.name),
            payload = credential.data.getBytes()
          )
        )
      }
      issueCredential = IssueCredential(
        body = body,
        attachments = attachments,
        from = requestCredential.to,
        to = requestCredential.from,
        thid = requestCredential.thid
      )
      record <- markCredentialGenerated(record, issueCredential)
    } yield record
  }

  private def createAnonCredsCredential(
      record: IssueCredentialRecord
  ): URIO[WalletAccessContext, AnoncredCredential] = {
    for {
      credentialDefinitionId <- ZIO
        .fromOption(record.credentialDefinitionId)
        .orElse(ZIO.dieMessage(s"No credential definition Id found in record: ${record.id}"))
      credentialDefinition <- getCredentialDefinition(credentialDefinitionId)
      cd = anoncreds.AnoncredCredentialDefinition(credentialDefinition.definition.toString)
      offerCredential <- ZIO
        .fromOption(record.offerCredentialData)
        .orElse(ZIO.dieMessage(s"No offer credential data found in record: ${record.id}"))
      offerCredentialAttachmentData <- ZIO
        .fromOption(
          offerCredential.attachments
            .find(_.format.contains(IssueCredentialOfferFormat.Anoncred.name))
            .map(_.data)
            .flatMap {
              case Base64(value) => Some(new String(java.util.Base64.getUrlDecoder.decode(value)))
              case _             => None
            }
        )
        .orElse(ZIO.dieMessage(s"No 'AnonCreds' offer credential attachment found in record: ${record.id}"))
      credentialOffer = anoncreds.AnoncredCredentialOffer(offerCredentialAttachmentData)
      requestCredential <- ZIO
        .fromOption(record.requestCredentialData)
        .orElse(ZIO.dieMessage(s"No request credential data found in record: ${record.id}"))
      requestCredentialAttachmentData <- ZIO
        .fromOption(
          requestCredential.attachments
            .find(_.format.contains(IssueCredentialRequestFormat.Anoncred.name))
            .map(_.data)
            .flatMap {
              case Base64(value) => Some(new String(java.util.Base64.getUrlDecoder.decode(value)))
              case _             => None
            }
        )
        .orElse(ZIO.dieMessage(s"No 'AnonCreds' request credential attachment found in record: ${record.id}"))
      credentialRequest = anoncreds.AnoncredCredentialRequest(requestCredentialAttachmentData)
      attrValues = offerCredential.body.credential_preview.body.attributes.map { attr =>
        (attr.name, attr.value)
      }
      credentialDefinitionSecret <- getCredentialDefinitionPrivatePart(credentialDefinition.guid)
      cdp = anoncreds.AnoncredCredentialDefinitionPrivate(credentialDefinitionSecret.json.toString)
      credential =
        AnoncredLib.createCredential(
          cd,
          cdp,
          credentialOffer,
          credentialRequest,
          attrValues
        )
    } yield credential
  }

  private def getOptionsFromOfferCredentialData(record: IssueCredentialRecord): UIO[Option[Options]] = {
    for {
      offer <- ZIO
        .fromOption(record.offerCredentialData)
        .orElse(ZIO.dieMessage(s"Offer data not found in record: ${record.id}"))
      attachmentDescriptor <- ZIO
        .fromOption(offer.attachments.headOption)
        .orElse(ZIO.dieMessage(s"Attachments not found in record: ${record.id}"))
      json <- attachmentDescriptor.data match
        case JsonData(json) => ZIO.succeed(json)
        case _              => ZIO.dieMessage(s"Attachment doesn't contain JsonData: ${record.id}")
      maybeOptions <- ZIO
        .fromEither(json.as[PresentationAttachment].map(_.options))
        .flatMapError(err => ZIO.dieMessage(err))
    } yield maybeOptions
  }

  private def getJwtFromRequestCredentialData(record: IssueCredentialRecord): UIO[JWT] = {
    for {
      request <- ZIO
        .fromOption(record.requestCredentialData)
        .orElse(ZIO.dieMessage(s"Request data not found in record: ${record.id}"))
      attachmentDescriptor <- ZIO
        .fromOption(request.attachments.headOption)
        .orElse(ZIO.dieMessage(s"Attachment not found in record: ${record.id}"))
      jwt <- attachmentDescriptor.data match
        case Base64(b64) =>
          ZIO.succeed {
            val base64Decoded = new String(java.util.Base64.getUrlDecoder.decode(b64))
            JWT(base64Decoded)
          }
        case _ => ZIO.dieMessage(s"Attachment does not contain Base64Data: ${record.id}")
    } yield jwt
  }

  private def validateRequestCredentialDataProof(
      maybeOptions: Option[Options],
      jwt: JWT
  ): IO[CredentialRequestValidationFailed, JwtPresentationPayload] = {
    for {
      _ <- maybeOptions match
        case None => ZIO.unit
        case Some(options) =>
          JwtPresentation.validatePresentation(jwt, options.domain, options.challenge) match
            case ZValidation.Success(log, value) => ZIO.unit
            case ZValidation.Failure(log, error) =>
              ZIO.fail(
                CredentialRequestValidationFailed(s"JWTPresentation validation failed: ${error.toList.mkString(";")}")
              )

      clock = java.time.Clock.system(ZoneId.systemDefault)
      verificationResult <- JwtPresentation
        .verify(
          jwt,
          JwtPresentation.PresentationVerificationOptions(
            maybeProofPurpose = Some(VerificationRelationship.Authentication),
            verifySignature = true,
            verifyDates = false,
            leeway = Duration.Zero
          )
        )(didResolver, uriResolver)(clock)
        .mapError(errors => CredentialRequestValidationFailed(errors*))

      result <- verificationResult match
        case ZValidation.Success(log, value) => ZIO.unit
        case ZValidation.Failure(log, error) =>
          ZIO.fail(CredentialRequestValidationFailed(s"JWT presentation verification failed: $error"))

      jwtPresentation <- ZIO
        .fromTry(JwtPresentation.decodeJwt[JwtPresentationPayload](jwt))
        .mapError(t => CredentialRequestValidationFailed(s"JWT presentation decoding failed: ${t.getMessage}"))
    } yield jwtPresentation
  }

  override def getCredentialOfferInvitation(
      pairwiseHolderDID: DidId,
      invitation: String
  ): ZIO[WalletAccessContext, CredentialServiceError, OfferCredential] = {
    for {
      invitation <- ZIO
        .fromEither(Base64Utils.decodeUrlToString(invitation).fromJson[Invitation])
        .mapError(err => InvitationParsingError(err))
      _ <- invitation.expires_time match {
        case Some(expiryTime) =>
          ZIO
            .fail(InvitationExpired(expiryTime))
            .when(Instant.now().getEpochSecond > expiryTime)
        case None => ZIO.unit
      }
      _ <- getIssueCredentialRecordByThreadId(DidCommID(invitation.id), false)
        .flatMap {
          case None    => ZIO.unit
          case Some(_) => ZIO.fail(InvitationAlreadyReceived(invitation.id))
        }
      credentialOffer <- ZIO.fromEither {
        invitation.attachments
          .flatMap(
            _.headOption.map(attachment =>
              attachment.data.toJson
                .fromJson[org.hyperledger.identus.mercury.model.JsonData]
                .flatMap { data =>
                  OfferCredential.given_JsonDecoder_OfferCredential
                    .decodeJson(data.json.toJson)
                    .map(r => r.copy(to = Some(pairwiseHolderDID)))
                    .leftMap(err =>
                      CredentialOfferDecodingError(
                        s"Credential Offer As Attachment decoding error: $err"
                      )
                    )
                }
                .leftMap(err => CredentialOfferDecodingError(s"Invitation Attachment JsonData decoding error: $err"))
            )
          )
          .getOrElse(
            Left(MissingInvitationAttachment("Missing Invitation Attachment for Credential Offer"))
          )
      }
    } yield credentialOffer

  }
}
