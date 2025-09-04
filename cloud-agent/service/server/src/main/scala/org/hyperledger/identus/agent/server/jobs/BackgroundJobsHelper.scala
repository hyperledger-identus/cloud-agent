package org.hyperledger.identus.agent.server.jobs

import org.hyperledger.identus.agent.walletapi.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.agent.walletapi.model.error.DIDSecretStorageError.{KeyNotFoundError, WalletNotFoundError}
import org.hyperledger.identus.agent.walletapi.model.error.GetManagedDIDError
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.{
  EllipticCurve,
  LongFormPrismDID,
  PrismDID,
  VerificationRelationship
}
import org.hyperledger.identus.castor.core.model.error.DIDResolutionError
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.mercury.{AgentPeerService, DidAgent}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import org.hyperledger.identus.pollux.core.model.error.{CredentialServiceError, PresentationError}
import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.pollux.core.service.CredentialService
import org.hyperledger.identus.pollux.vc.jwt.{
  DIDResolutionFailed,
  DIDResolutionSucceeded,
  DidResolver as JwtDidResolver,
  ES256KSigner,
  Issuer as JwtIssuer,
  *
}
import org.hyperledger.identus.shared.crypto.*
import org.hyperledger.identus.shared.messaging.ConsumerJobConfig
import org.hyperledger.identus.shared.messaging.MessagingService.RetryStep
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.{durationInt, Duration, ZIO, ZLayer}
import zio.prelude.OrdOps

import java.time.Instant
import java.util.Base64

trait BackgroundJobsHelper {

  def getLongForm(
      did: PrismDID,
      allowUnpublishedIssuingDID: Boolean = false
  ): ZIO[ManagedDIDService & WalletAccessContext, BackgroundJobError | GetManagedDIDError, LongFormPrismDID] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      didState <- managedDIDService
        .getManagedDIDState(did.asCanonical)
        .someOrFail(BackgroundJobError.InvalidState(s"Issuer DID does not exist in the wallet: $did"))
        .flatMap {
          case s @ ManagedDIDState(_, _, PublicationState.Published(_)) => ZIO.succeed(s)
          case s =>
            ZIO.cond(
              allowUnpublishedIssuingDID,
              s,
              BackgroundJobError.InvalidState(s"Issuer DID must be published: $did")
            )
        }
      longFormPrismDID = PrismDID.buildLongFormFromOperation(didState.createOperation)
    } yield longFormPrismDID
  }

  def createJwtVcIssuer(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      kidIssuer: Option[KeyId],
  ): ZIO[
    DIDService & ManagedDIDService & WalletAccessContext,
    BackgroundJobError | GetManagedDIDError | DIDResolutionError,
    JwtIssuer
  ] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      didService <- ZIO.service[DIDService]
      // Automatically infer keyId to use by resolving DID and choose the corresponding VerificationRelationship
      // The key detection should have parity with `CredentialServiceImpl.getKeyId`
      issuingKeyId <- didService
        .resolveDID(jwtIssuerDID)
        .someOrFail(BackgroundJobError.InvalidState(s"Issuing DID resolution result is not found"))
        .map { case (_, didData) =>
          val matchingKeys = didData.publicKeys.filter(pk => pk.purpose == verificationRelationship)
          (matchingKeys, kidIssuer) match {
            case (Seq(), _)                => None
            case (Seq(singleKey), None)    => Some(singleKey.id)
            case (multipleKeys, Some(kid)) => multipleKeys.find(_.id.value.endsWith(kid.value)).map(_.id)
            case (_, None)                 => None
          }
        }
        .someOrFail(
          BackgroundJobError.InvalidState(
            s"Issuing DID doesn't have a key in ${verificationRelationship.name} with kid ${kidIssuer.map(_.value)} to use: $jwtIssuerDID"
          )
        )
      jwtIssuer <- managedDIDService
        .findDIDKeyPair(jwtIssuerDID.asCanonical, issuingKeyId)
        .flatMap {
          case None =>
            ZIO.fail(
              BackgroundJobError
                .InvalidState(s"Issuer key-pair does not exist in the wallet: ${jwtIssuerDID.toString}#$issuingKeyId")
            )
          case Some(Ed25519KeyPair(publicKey, privateKey)) =>
            ZIO.succeed(
              JwtIssuer(
                jwtIssuerDID.did,
                // org.hyperledger.identus.castor.core.model.did.DID.fromStringUnsafe(jwtIssuerDID.toString),
                EdSigner(Ed25519KeyPair(publicKey, privateKey), Some(issuingKeyId)),
                publicKey.toJava
              )
            )
          case Some(X25519KeyPair(publicKey, privateKey)) =>
            ZIO.fail(
              BackgroundJobError.InvalidState(
                s"Issuer key-pair '$issuingKeyId' is of the type X25519. It's not supported by this feature in this version"
              )
            )
          case Some(Secp256k1KeyPair(publicKey, privateKey)) =>
            ZIO.succeed(
              JwtIssuer(
                jwtIssuerDID.did,
                ES256KSigner(privateKey.toJavaPrivateKey, Some(issuingKeyId)),
                publicKey.toJavaPublicKey
              )
            )
        }
    } yield jwtIssuer
  }

  def buildDIDCommAgent(
      myDid: DidId
  ): ZIO[ManagedDIDService & WalletAccessContext, KeyNotFoundError, ZLayer[Any, Nothing, DidAgent]] = {
    for {
      managedDidService <- ZIO.service[ManagedDIDService]
      peerDID <- managedDidService.getPeerDID(myDid)
      agent = AgentPeerService.makeLayer(peerDID)
    } yield agent
  }

  def buildWalletAccessContextLayer(
      myDid: DidId
  ): ZIO[DIDNonSecretStorage, WalletNotFoundError, WalletAccessContext] = {
    for {
      nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
      maybePeerDIDRecord <- nonSecretStorage.getPeerDIDRecord(myDid).orDie
      peerDIDRecord <- ZIO.fromOption(maybePeerDIDRecord).mapError(_ => WalletNotFoundError(myDid))
      _ <- ZIO.logInfo(s"PeerDID record successfully loaded in DIDComm receiver endpoint: $peerDIDRecord")
      walletAccessContext = WalletAccessContext(peerDIDRecord.walletId)
    } yield walletAccessContext
  }

  def findHolderEd25519SigningKey(
      proverDid: PrismDID,
      verificationRelationship: VerificationRelationship,
      keyId: KeyId
  ): ZIO[
    DIDService & ManagedDIDService & WalletAccessContext,
    DIDResolutionError | BackgroundJobError,
    Ed25519KeyPair
  ] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      didService <- ZIO.service[DIDService]
      issuingKeyId <- didService
        .resolveDID(proverDid)
        .mapError(e =>
          BackgroundJobError.InvalidState(
            s"Error occured while resolving Issuing DID during VC creation: ${e.toString}"
          )
        )
        .someOrFail(BackgroundJobError.InvalidState(s"Issuing DID resolution result is not found"))
        .map { case (_, didData) =>
          didData.publicKeys
            .find(pk =>
              pk.id == keyId
                && pk.purpose == verificationRelationship && pk.publicKeyData.crv == EllipticCurve.ED25519
            )
            .map(_.id)
        }
        .someOrFail(
          BackgroundJobError.InvalidState(
            s"Issuing DID doesn't have a key in ${verificationRelationship.name} to use: $proverDid"
          )
        )
      ed25519keyPair <- managedDIDService
        .findDIDKeyPair(proverDid.asCanonical, issuingKeyId)
        .map(_.collect { case keyPair: Ed25519KeyPair => keyPair })
        .someOrFail(
          BackgroundJobError.InvalidState(
            s"Issuer key-pair does not exist in the wallet: ${proverDid.toString}#$issuingKeyId"
          )
        )
    } yield ed25519keyPair
  }

  def resolveToEd25519PublicKey(did: String): ZIO[JwtDidResolver, PresentationError, Ed25519PublicKey] = {
    for {
      didResolverService <- ZIO.service[JwtDidResolver]
      didResolutionResult <- didResolverService.resolve(did)
      publicKeyBase64 <- didResolutionResult match {
        case failed: DIDResolutionFailed =>
          ZIO.fail(
            PresentationError.DIDResolutionFailed(did, failed.error.toString)
          )
        case succeeded: DIDResolutionSucceeded =>
          succeeded.didDocument.verificationMethod
            .find(vm => succeeded.didDocument.assertionMethod.contains(vm.id))
            .flatMap(_.publicKeyJwk.flatMap(_.x))
            .toRight(PresentationError.DIDDocumentMissing(did))
            .fold(ZIO.fail(_), ZIO.succeed(_))
      }
      ed25519PublicKey <- ZIO
        .fromTry {
          val decodedKey = Base64.getUrlDecoder.decode(publicKeyBase64)
          KmpEd25519KeyOps.publicKeyFromEncoded(decodedKey)
        }
        .mapError(t => PresentationError.PublicKeyDecodingError(t.getMessage))
    } yield ed25519PublicKey
  }

  def checkInvitationExpiry(
      id: DidCommID,
      invitation: Option[Invitation]
  ): ZIO[CredentialService & WalletAccessContext, CredentialServiceError, Unit] = {
    invitation.flatMap(_.expires_time) match {
      case Some(expiryTime) if Instant.now().getEpochSecond > expiryTime =>
        for {
          service <- ZIO.service[CredentialService]
          _ <- service.markCredentialOfferInvitationExpired(id)
          _ <- ZIO.fail(CredentialServiceError.InvitationExpired(expiryTime))
        } yield ()
      case _ => ZIO.unit
    }
  }

  def retryStepsFromConfig(topicName: String, jobConfig: ConsumerJobConfig): Seq[RetryStep] = {
    val retryTopics = jobConfig.retryStrategy match
      case None => Seq.empty
      case Some(rs) =>
        (1 to rs.maxRetries).map(i =>
          (
            s"$topicName-retry-$i",
            rs.initialDelay.multipliedBy(Math.pow(2, i - 1).toLong).min(rs.maxDelay)
          )
        )
    val topics = retryTopics prepended (topicName, 0.seconds) appended (s"$topicName-DLQ", Duration.Infinity)
    (0 until topics.size - 1).map { i =>
      RetryStep(topics(i)._1, jobConfig.consumerCount, topics(i)._2, topics(i + 1)._1)
    }
  }
}
