package org.hyperledger.identus.pollux.core.service.verification

import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.vc.jwt.{
  CredentialSchema as JwtCredentialSchema,
  DidResolver,
  JWT,
  JWTVerification,
  JwtCredential
}
import org.hyperledger.identus.shared.http.UriResolver
import zio.*
import zio.json.EncoderOps

import java.time.OffsetDateTime

class VcVerificationServiceImpl(didResolver: DidResolver, uriResolver: UriResolver) extends VcVerificationService {
  override def verify(
      vcVerificationRequests: List[VcVerificationRequest]
  ): IO[VcVerificationServiceError, List[VcVerificationResult]] = {
    ZIO.collectAll(
      vcVerificationRequests.map(vcVerificationRequest =>
        verify(vcVerificationRequest.credential, vcVerificationRequest.verification)
      )
    )
  }

  private def verify(
      credential: String,
      verification: VcVerification,
  ): IO[VcVerificationServiceError, VcVerificationResult] = {
    verification match {
      case VcVerification.SchemaCheck               => verifySchema(credential)
      case VcVerification.SignatureVerification     => verifySignature(credential)
      case VcVerification.ExpirationCheck(dateTime) => verifyExpiration(credential, dateTime)
      case VcVerification.NotBeforeCheck(dateTime)  => verifyNotBefore(credential, dateTime)
      case VcVerification.AlgorithmVerification     => verifyAlgorithm(credential)
      case VcVerification.IssuerIdentification(iss) => verifyIssuerIdentification(credential, iss)
      case VcVerification.SubjectVerification       => verifySubjectVerification(credential)
      case VcVerification.SemanticCheckOfClaims     => verifySemanticCheckOfClaims(credential)
      case VcVerification.AudienceCheck(aud)        => verifyAudienceCheck(credential, aud)
      case _                                        =>
        ZIO.fail(
          VcVerificationServiceError.UnexpectedError(
            s"Unsupported Verification:$verification"
          )
        )
    }
  }

  private def verifySchema(credential: String): IO[VcVerificationServiceError, VcVerificationResult] = {
    val result =
      for {
        decodedJwt <-
          JwtCredential
            .decodeJwt(JWT(credential))
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Unable to decode JWT: $error"))
        credentialSchema <-
          ZIO
            .fromOption(decodedJwt.maybeCredentialSchema)
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Missing Credential Schema: $error"))
        credentialSchemas = credentialSchema match {
          case schema: JwtCredentialSchema           => List(schema)
          case schemaList: List[JwtCredentialSchema] => schemaList
        }
        schemaUris <- ZIO
          .collectAll(credentialSchemas.map(cs => UriString.make(cs.id).toZIO))
          .mapError(error => VcVerificationServiceError.UnexpectedError(s"Invalid schema URI: $error"))
        result <-
          ZIO.collectAll(
            schemaUris.map(schemaUri =>
              CredentialSchema
                .validSchemaValidator(
                  schemaUri,
                  uriResolver
                )
                .mapError(error => VcVerificationServiceError.UnexpectedError(s"Schema Validator Failed: $error"))
            )
          )
      } yield result

    result
      .as(
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.SchemaCheck,
          success = true
        )
      )
      .catchAll(_ =>
        ZIO.succeed(
          VcVerificationResult(
            credential = credential,
            verification = VcVerification.SchemaCheck,
            success = false
          )
        )
      )
  }

  private def verifySubjectVerification(credential: String): IO[VcVerificationServiceError, VcVerificationResult] = {
    val result =
      for {
        decodedJwt <-
          JwtCredential
            .decodeJwt(JWT(credential))
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Unable decode JWT: $error"))
        credentialSchema <-
          ZIO
            .fromOption(decodedJwt.maybeCredentialSchema)
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Missing Credential Schema: $error"))
        credentialSchemas = credentialSchema match {
          case schema: JwtCredentialSchema           => List(schema)
          case schemaList: List[JwtCredentialSchema] => schemaList
        }
        schemaUris <- ZIO
          .collectAll(credentialSchemas.map(cs => UriString.make(cs.id).toZIO))
          .mapError(error => VcVerificationServiceError.UnexpectedError(s"Invalid schema URI: $error"))
        result <-
          ZIO.collectAll(
            schemaUris.map(schemaUri =>
              CredentialSchema
                .validateJWTCredentialSubject(
                  schemaUri,
                  decodedJwt.vc.toJson,
                  uriResolver
                )
                .mapError(error =>
                  VcVerificationServiceError.UnexpectedError(s"JWT Credential Subject Validation Failed: $error")
                )
            )
          )
      } yield result

    result
      .as(
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.SubjectVerification,
          success = true
        )
      )
      .catchAll(_ =>
        ZIO.succeed(
          VcVerificationResult(
            credential = credential,
            verification = VcVerification.SubjectVerification,
            success = false
          )
        )
      )
  }

  private def verifySignature(credential: String): IO[VcVerificationServiceError, VcVerificationResult] = {
    JwtCredential
      .validateEncodedJWT(JWT(credential))(didResolver)
      .mapError(error => VcVerificationServiceError.UnexpectedError(error))
      .map(validation =>
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.SignatureVerification,
          success = validation
            .map(_ => true)
            .getOrElse(false)
        )
      )
  }

  private def verifyExpiration(
      credential: String,
      dateTime: OffsetDateTime
  ): IO[VcVerificationServiceError, VcVerificationResult] = {
    ZIO.succeed(
      VcVerificationResult(
        credential = credential,
        verification = VcVerification.ExpirationCheck(dateTime),
        success = JwtCredential
          .validateExpiration(JWT(credential), dateTime)
          .map(_ => true)
          .getOrElse(false)
      )
    )
  }

  private def verifyNotBefore(
      credential: String,
      dateTime: OffsetDateTime
  ): IO[VcVerificationServiceError, VcVerificationResult] = {
    ZIO.succeed(
      VcVerificationResult(
        credential = credential,
        verification = VcVerification.NotBeforeCheck(dateTime),
        success = JwtCredential
          .validateNotBefore(JWT(credential), dateTime)
          .map(_ => true)
          .getOrElse(false)
      )
    )
  }

  private def verifyAlgorithm(credential: String): IO[VcVerificationServiceError, VcVerificationResult] = {
    ZIO.succeed(
      VcVerificationResult(
        credential = credential,
        verification = VcVerification.AlgorithmVerification,
        success = JWTVerification
          .validateAlgorithm(JWT(credential))
          .map(_ => true)
          .getOrElse(false)
      )
    )
  }

  private def verifyIssuerIdentification(
      credential: String,
      iss: String
  ): IO[VcVerificationServiceError, VcVerificationResult] = {
    val result =
      for {
        decodedJwt <-
          JwtCredential
            .decodeJwt(JWT(credential))
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Unable decode JWT: $error"))
      } yield decodedJwt.iss.contains(iss)

    result
      .map(success =>
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.IssuerIdentification(iss),
          success = success
        )
      )
  }

  private def verifySemanticCheckOfClaims(credential: String): IO[VcVerificationServiceError, VcVerificationResult] = {
    val result =
      for {
        decodedJwt <-
          JwtCredential
            .decodeJwt(JWT(credential))
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Unable decode JWT: $error"))
      } yield decodedJwt

    result
      .as(
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.SemanticCheckOfClaims,
          success = true
        )
      )
      .catchAll(_ =>
        ZIO.succeed(
          VcVerificationResult(
            credential = credential,
            verification = VcVerification.SemanticCheckOfClaims,
            success = false
          )
        )
      )
  }

  private def verifyAudienceCheck(
      credential: String,
      aud: String
  ): IO[VcVerificationServiceError, VcVerificationResult] = {
    val result =
      for {
        decodedJwt <-
          JwtCredential
            .decodeJwt(JWT(credential))
            .mapError(error => VcVerificationServiceError.UnexpectedError(s"Unable decode JWT: $error"))
      } yield decodedJwt.aud.contains(aud)

    result
      .map(success =>
        VcVerificationResult(
          credential = credential,
          verification = VcVerification.AudienceCheck(aud),
          success = success
        )
      )
  }
}

object VcVerificationServiceImpl {
  val layer: URLayer[DidResolver & UriResolver, VcVerificationService] =
    ZLayer.fromFunction(VcVerificationServiceImpl(_, _))
}
