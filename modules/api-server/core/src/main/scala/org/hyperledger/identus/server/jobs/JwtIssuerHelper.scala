package org.hyperledger.identus.server.jobs

import org.hyperledger.identus.credentials.vc.jwt.{ES256KSigner, EdSigner, Issuer as JwtIssuer}
import org.hyperledger.identus.did.core.model.did.{EllipticCurve, PrismDID, VerificationRelationship}
import org.hyperledger.identus.did.core.model.error.DIDResolutionError
import org.hyperledger.identus.did.core.service.DIDService
import org.hyperledger.identus.shared.crypto.*
import org.hyperledger.identus.shared.models.{Failure, KeyId, WalletAccessContext}
import org.hyperledger.identus.wallet.service.ManagedDIDService
import zio.ZIO

trait JwtIssuerHelper {

  def createJwtVcIssuer(
      jwtIssuerDID: PrismDID,
      verificationRelationship: VerificationRelationship,
      kidIssuer: Option[KeyId],
  ): ZIO[
    DIDService & ManagedDIDService & WalletAccessContext,
    DIDResolutionError | Failure,
    JwtIssuer
  ] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      didService <- ZIO.service[DIDService]
      issuingKeyId <- didService
        .resolveDID(jwtIssuerDID)
        .someOrFail(BackgroundJobError.InvalidState(s"Issuing DID resolution result is not found"))
        .map { case (_, didData) =>
          val allowedCrv = Set(EllipticCurve.ED25519, EllipticCurve.SECP256K1)
          val matchingKeys = didData.publicKeys
            .filter(pk => pk.purpose == verificationRelationship && allowedCrv.contains(pk.publicKeyData.crv))
          (matchingKeys.toList, kidIssuer) match {
            case (Nil, _)              => None
            case (firstKey :: _, None) => Some(firstKey.id)
            case (keys, Some(kid))     => keys.find(_.id.value.endsWith(kid.value)).map(_.id)
          }
        }
        .someOrFail(
          BackgroundJobError.InvalidState(
            s"Issuing DID doesn't have a key in ${verificationRelationship.name} to use: $jwtIssuerDID"
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
}
