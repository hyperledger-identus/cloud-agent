package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.credentials.core.model.CredentialSchemaAndTrustedIssuersConstraint
import org.hyperledger.identus.credentials.vc.jwt.revocation.BitString
import org.hyperledger.identus.did.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.crypto.Ed25519KeyPair
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

import java.security.{PrivateKey, PublicKey}
import java.time.OffsetDateTime

/** A test-only stub Signer that produces a fake JWT (not cryptographically signed). Suitable for tests that don't
  * verify actual signatures.
  */
private class TestSigner extends Signer {
  override def encode(claim: Json): JWT = {
    val header = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"alg":"none","typ":"JWT"}""".getBytes)
    val payload = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(claim.toString.getBytes)
    JWT(s"$header.$payload.test-signature")
  }

  override def generateProofForJson(payload: Json, pk: PublicKey): Task[Proof] =
    ZIO.fail(Throwable("TestSigner.generateProofForJson not implemented"))
}

class VcJwtServiceStub extends VcJwtService {

  private def decodeJwtPayload(jwt: JWT): Either[String, Json] = {
    val parts = jwt.value.split("\\.")
    if (parts.length < 2) Left("Invalid JWT format")
    else {
      val payloadJson = new String(java.util.Base64.getUrlDecoder.decode(parts(1)))
      payloadJson.fromJson[Json]
    }
  }

  override def createES256KSigner(privateKey: PrivateKey, keyId: Option[KeyId]): Signer =
    TestSigner()

  override def createEdSigner(ed25519KeyPair: Ed25519KeyPair, keyId: Option[KeyId]): Signer =
    TestSigner()

  override def encodeCredentialToJwt(payload: W3cCredentialPayload, issuer: Issuer): JWT =
    issuer.signer.encode(payload.toJwtCredentialPayload.toJsonAST.toOption.get)

  override def decodeCredentialJwt(jwt: JWT): IO[String, JwtCredentialPayload] =
    ZIO.fromEither(decodeJwtPayload(jwt).flatMap(_.as[JwtCredentialPayload]))

  override def encodePresentationJwt(payload: JwtPresentationPayload, issuer: Issuer): JWT =
    issuer.signer.encode(payload.toJsonAST.toOption.get)

  override def encodePresentationToJwt(payload: W3cPresentationPayload, issuer: Issuer): JWT =
    encodePresentationJwt(payload.toJwtPresentationPayload, issuer)

  override def decodePresentationJwt(jwt: JWT): IO[String, JwtPresentationPayload] =
    ZIO.fromEither(decodeJwtPayload(jwt).flatMap(_.as[JwtPresentationPayload]))

  override def validateCredentialSignature(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship]
  )(didResolver: DidResolver): IO[String, Boolean] =
    ZIO.succeed(true)

  override def validateExpiration(jwt: JWT, dateTime: OffsetDateTime): Boolean = {
    decodeJwtPayload(jwt).flatMap(_.as[JwtCredentialPayload]) match {
      case Right(payload) =>
        payload.maybeExp.forall(exp => dateTime.toInstant.isBefore(exp))
      case Left(_) => true
    }
  }

  override def validateNotBefore(jwt: JWT, dateTime: OffsetDateTime): Boolean = {
    decodeJwtPayload(jwt).flatMap(_.as[JwtCredentialPayload]) match {
      case Right(payload) =>
        !dateTime.toInstant.isBefore(payload.nbf)
      case Left(_) => true
    }
  }

  override def validateAlgorithm(jwt: JWT): Boolean = true

  override def validatePresentation(jwt: JWT, domain: String, challenge: String): Either[List[String], Unit] =
    Right(())

  override def validatePresentationClaims(
      jwt: JWT,
      domain: Option[String],
      challenge: Option[String],
      schemaIdAndTrustedIssuers: Seq[CredentialSchemaAndTrustedIssuersConstraint]
  ): Either[List[String], Unit] =
    Right(())

  override def verifyPresentation(
      jwt: JWT,
      options: PresentationVerificationOptions
  )(didResolver: DidResolver, uriResolver: UriResolver): IO[List[String], Boolean] =
    ZIO.succeed(true)

  override def extractJwtHeaderKeyId(jwt: JWT): Either[String, Option[String]] =
    Right(None)

  override def buildStatusListCredential(vcId: String, revocationData: BitString, jwtIssuer: Issuer): Task[Json] =
    ZIO.succeed(Json.Obj("id" -> Json.Str(vcId), "type" -> Json.Str("StatusList2021Credential")))
}

object VcJwtServiceStub {
  val layer: ULayer[VcJwtService] = ZLayer.succeed(VcJwtServiceStub())
}
