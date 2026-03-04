package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.vc.jwt.revocation.BitString
import org.hyperledger.identus.did.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.crypto.Ed25519KeyPair
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.json.ast.Json

import java.security.PrivateKey
import java.time.OffsetDateTime
import java.time.temporal.TemporalAmount

trait VcJwtService {
  // Signer creation (abstracts ES256KSigner/EdSigner construction)
  def createES256KSigner(privateKey: PrivateKey, keyId: Option[KeyId]): Signer
  def createEdSigner(ed25519KeyPair: Ed25519KeyPair, keyId: Option[KeyId]): Signer

  // JWT credential encode/decode
  def encodeCredentialToJwt(payload: W3cCredentialPayload, issuer: Issuer): JWT
  def decodeCredentialJwt(jwt: JWT): IO[String, JwtCredentialPayload]

  // JWT presentation encode/decode
  def encodePresentationJwt(payload: JwtPresentationPayload, issuer: Issuer): JWT
  def decodePresentationJwt(jwt: JWT): IO[String, JwtPresentationPayload]

  // Credential verification
  def validateCredentialSignature(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship]
  )(didResolver: DidResolver): IO[String, Boolean]
  def validateExpiration(jwt: JWT, dateTime: OffsetDateTime): Boolean
  def validateNotBefore(jwt: JWT, dateTime: OffsetDateTime): Boolean
  def validateAlgorithm(jwt: JWT): Boolean

  // Presentation verification
  def validatePresentation(jwt: JWT, domain: String, challenge: String): Either[List[String], Unit]
  def verifyPresentation(
      jwt: JWT,
      options: PresentationVerificationOptions
  )(didResolver: DidResolver, uriResolver: UriResolver): IO[List[String], Boolean]
  def extractJwtHeaderKeyId(jwt: JWT): Either[String, Option[String]]

  // Status list
  def buildStatusListCredential(vcId: String, revocationData: BitString, jwtIssuer: Issuer): Task[Json]
}

object VcJwtService {
  def layer: URLayer[VcJwtService, VcJwtService] = ZLayer.service[VcJwtService]
}

/** Configuration for presentation verification. */
case class PresentationVerificationOptions(
    verifySignature: Boolean = true,
    verifyDates: Boolean = false,
    verifyHoldersBinding: Boolean = false,
    leeway: TemporalAmount = Duration.Zero,
    maybeCredentialOptions: Option[CredentialVerificationOptions] = None,
    maybeProofPurpose: Option[VerificationRelationship] = None
)

/** Configuration for credential verification within a presentation. */
case class CredentialVerificationOptions(
    verifySignature: Boolean = true,
    verifyDates: Boolean = false,
    leeway: TemporalAmount = Duration.Zero,
    maybeProofPurpose: Option[VerificationRelationship] = None
)
