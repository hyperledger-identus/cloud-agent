package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.vc.jwt.revocation.{BitString, VCStatusList2021}
import org.hyperledger.identus.did.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.crypto.Ed25519KeyPair
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.json.ast.Json
import zio.prelude.Validation

import java.security.PrivateKey
import java.time.{Clock, OffsetDateTime}

class VcJwtServiceLive extends VcJwtService {

  override def createES256KSigner(privateKey: PrivateKey, keyId: Option[KeyId]): Signer =
    Signers.es256k(privateKey, keyId)

  override def createEdSigner(ed25519KeyPair: Ed25519KeyPair, keyId: Option[KeyId]): Signer =
    Signers.ed(ed25519KeyPair, keyId)

  override def encodeCredentialToJwt(payload: W3cCredentialPayload, issuer: Issuer): JWT =
    W3CCredential.toEncodedJwt(payload, issuer)

  override def decodeCredentialJwt(jwt: JWT): IO[String, JwtCredentialPayload] =
    JwtCredential.decodeJwt(jwt)

  override def encodePresentationJwt(payload: JwtPresentationPayload, issuer: Issuer): JWT =
    JwtPresentation.encodeJwt(payload, issuer)

  override def decodePresentationJwt(jwt: JWT): IO[String, JwtPresentationPayload] = {
    ZIO
      .fromTry(JwtPresentation.decodeJwt[JwtPresentationPayload](jwt))
      .mapError(_.getMessage)
  }

  override def validateCredentialSignature(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship]
  )(didResolver: DidResolver): IO[String, Boolean] = {
    JwtCredential
      .validateEncodedJWT(jwt, proofPurpose)(didResolver)
      .map(_.fold(_ => false, _ => true))
  }

  override def validateExpiration(jwt: JWT, dateTime: OffsetDateTime): Boolean =
    JwtCredential.validateExpiration(jwt, dateTime).fold(_ => false, _ => true)

  override def validateNotBefore(jwt: JWT, dateTime: OffsetDateTime): Boolean =
    JwtCredential.validateNotBefore(jwt, dateTime).fold(_ => false, _ => true)

  override def validateAlgorithm(jwt: JWT): Boolean =
    JWTVerification.validateAlgorithm(jwt).fold(_ => false, _ => true)

  override def validatePresentation(jwt: JWT, domain: String, challenge: String): Either[List[String], Unit] = {
    val result = JwtPresentation.validatePresentation(jwt, domain, challenge)
    result.toEither.left.map(_.toList)
  }

  override def verifyPresentation(
      jwt: JWT,
      options: PresentationVerificationOptions
  )(didResolver: DidResolver, uriResolver: UriResolver): IO[List[String], Boolean] = {
    // Convert our PresentationVerificationOptions to JwtPresentation's internal type
    val internalOptions = JwtPresentation.PresentationVerificationOptions(
      verifySignature = options.verifySignature,
      verifyDates = options.verifyDates,
      verifyHoldersBinding = options.verifyHoldersBinding,
      leeway = options.leeway,
      maybeCredentialOptions = options.maybeCredentialOptions.map(co =>
        CredentialVerification.CredentialVerificationOptions(
          verifySignature = co.verifySignature,
          verifyDates = co.verifyDates,
          leeway = co.leeway,
          maybeProofPurpose = co.maybeProofPurpose
        )
      ),
      maybeProofPurpose = options.maybeProofPurpose
    )
    given Clock = Clock.systemUTC()
    JwtPresentation
      .verify(jwt, internalOptions)(didResolver, uriResolver)
      .map(_.fold(_ => false, _ => true))
  }

  override def extractJwtHeaderKeyId(jwt: JWT): Either[String, Option[String]] = {
    JWTVerification.extractJwtHeader(jwt) match {
      case Validation.Success(_, header) => Right(header.keyId)
      case Validation.Failure(_, errors) => Left(errors.toList.mkString("; "))
    }
  }

  override def buildStatusListCredential(vcId: String, revocationData: BitString, jwtIssuer: Issuer): Task[Json] = {
    for {
      statusListCredential <- VCStatusList2021
        .build(vcId = vcId, revocationData = revocationData, jwtIssuer = jwtIssuer)
        .mapError(x => new Throwable(x.msg))
      json <- statusListCredential.toJsonWithEmbeddedProof
    } yield json
  }
}

object VcJwtServiceLive {
  val layer: ULayer[VcJwtService] = ZLayer.succeed(VcJwtServiceLive())
}
