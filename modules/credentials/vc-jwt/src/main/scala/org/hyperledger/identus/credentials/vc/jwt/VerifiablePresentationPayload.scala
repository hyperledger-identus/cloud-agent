package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.did.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.http.UriResolver
import pdi.jwt.{JwtOptions, JwtZIOJson}
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.prelude.*

import java.security.PublicKey
import java.time.{Clock, Instant}
import java.time.temporal.TemporalAmount
import scala.util.Try

object JwtPresentation {

  def encodeJwt(payload: JwtPresentationPayload, issuer: Issuer): JWT =
    issuer.signer.encode(payload.toJsonAST.toOption.get)

  def toEncodeW3C(payload: W3cPresentationPayload, issuer: Issuer): W3cVerifiablePresentationPayload = {
    W3cVerifiablePresentationPayload(
      payload = payload,
      proof = JwtProof(
        `type` = "JwtProof2020",
        jwt = issuer.signer.encode(payload.toJsonAST.toOption.get)
      )
    )
  }

  def toEncodedJwt(payload: W3cPresentationPayload, issuer: Issuer): JWT =
    encodeJwt(payload.toJwtPresentationPayload, issuer)

  def decodeJwt[A](jwt: JWT)(using decoder: zio.json.JsonDecoder[A]): Try[A] = {
    JwtZIOJson
      .decodeRaw(jwt.value, options = JwtOptions(signature = false, expiration = false, notBefore = false))
      .flatMap(a => a.fromJson[A].left.map(s => new RuntimeException(s)).toTry)
  }

  def validateEncodedJwt(jwt: JWT, publicKey: PublicKey): Validation[String, Unit] =
    JWTVerification.validateEncodedJwt(jwt, publicKey)

  def validateEncodedJWT(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship]
  )(didResolver: DidResolver): IO[String, Validation[String, Unit]] = {
    JWTVerification.validateEncodedJwt(jwt, proofPurpose)(didResolver: DidResolver)(claim =>
      Validation.fromEither(claim.fromJson[JwtPresentationPayload])
    )(_.iss)
  }

  def validateEncodedW3C(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship]
  )(didResolver: DidResolver): IO[String, Validation[String, Unit]] = {
    JWTVerification.validateEncodedJwt(jwt, proofPurpose)(didResolver: DidResolver)(claim =>
      Validation.fromEither(claim.fromJson[W3cPresentationPayload])
    )(_.holder)
  }

  def validateEnclosedCredentials(
      jwt: JWT,
      options: CredentialVerification.CredentialVerificationOptions
  )(didResolver: DidResolver, uriResolver: UriResolver)(implicit
      clock: Clock
  ): IO[List[String], Validation[String, Unit]] = {
    val validateJwtPresentation = Validation.fromTry(decodeJwt[JwtPresentationPayload](jwt)).mapError(_.toString)

    val credentialValidationZIO =
      ValidationUtils.foreach(
        validateJwtPresentation
          .map(validJwtPresentation =>
            validateCredentials(validJwtPresentation, options)(didResolver, uriResolver)(clock)
          )
      )(identity)

    credentialValidationZIO.map(validCredentialValidations => {
      for {
        credentialValidations <- validCredentialValidations
        _ <- Validation.validateAll(credentialValidations)
        success <- Validation.unit
      } yield success
    })
  }

  def validateCredentials(
      decodedJwtPresentation: JwtPresentationPayload,
      options: CredentialVerification.CredentialVerificationOptions
  )(didResolver: DidResolver, uriResolver: UriResolver)(implicit
      clock: Clock
  ): ZIO[Any, List[String], IndexedSeq[Validation[String, Unit]]] = {
    ZIO.validatePar(decodedJwtPresentation.vp.verifiableCredential) { a =>
      CredentialVerification.verify(a, options)(didResolver, uriResolver)(clock)
    }
  }

  def validatePresentation(
      jwt: JWT,
      domain: String,
      challenge: String
  ): Validation[String, Unit] = {
    val validateJwtPresentation = Validation.fromTry(decodeJwt[JwtPresentationPayload](jwt)).mapError(_.toString)
    for {
      decodeJwtPresentation <- validateJwtPresentation
      aud <- validateAudience(decodeJwtPresentation, Some(domain))
      result <- validateNonce(decodeJwtPresentation, Some(challenge))
    } yield result
  }

  def validatePresentation(
      jwt: JWT,
      domain: Option[String],
      challenge: Option[String],
      schemaIdAndTrustedIssuers: Seq[CredentialSchemaAndTrustedIssuersConstraint]
  ): Validation[String, Unit] = {
    val validateJwtPresentation = Validation.fromTry(decodeJwt[JwtPresentationPayload](jwt)).mapError(_.toString)
    for {
      decodeJwtPresentation <- validateJwtPresentation
      aud <- validateAudience(decodeJwtPresentation, domain)
      nonce <- validateNonce(decodeJwtPresentation, challenge)
      result <- validateSchemaIdAndTrustedIssuers(decodeJwtPresentation, schemaIdAndTrustedIssuers)
    } yield {
      result
    }
  }

  def validateSchemaIdAndTrustedIssuers(
      decodedJwtPresentation: JwtPresentationPayload,
      schemaIdAndTrustedIssuers: Seq[CredentialSchemaAndTrustedIssuersConstraint]
  ): Validation[String, Unit] = {

    val vcList = decodedJwtPresentation.vp.verifiableCredential
    val expectedSchemaIds = schemaIdAndTrustedIssuers.map(_.schemaId)
    val trustedIssuers = schemaIdAndTrustedIssuers.flatMap(_.trustedIssuers).flatten
    ZValidation
      .validateAll(
        vcList.map {
          case (w3cVerifiableCredentialPayload: W3cVerifiableCredentialPayload) =>
            val credentialSchemas = w3cVerifiableCredentialPayload.payload.maybeCredentialSchema
            val issuer = w3cVerifiableCredentialPayload.payload.issuer
            for {
              s <- validateSchemaIds(credentialSchemas, expectedSchemaIds)
              i <- validateIsTrustedIssuer(issuer, trustedIssuers)
            } yield i

          case (jwtVerifiableCredentialPayload: JwtVerifiableCredentialPayload) =>
            for {
              jwtCredentialPayload <- Validation
                .fromTry(decodeJwt[JwtCredentialPayload](jwtVerifiableCredentialPayload.jwt))
                .mapError(_.toString)
              issuer = jwtCredentialPayload.issuer
              credentialSchemas = jwtCredentialPayload.maybeCredentialSchema
              s <- validateSchemaIds(credentialSchemas, expectedSchemaIds)
              i <- validateIsTrustedIssuer(issuer, trustedIssuers)
            } yield i
        }
      )
      .map(_ => ())
  }
  def validateSchemaIds(
      credentialSchemas: Option[CredentialSchema | List[CredentialSchema]],
      expectedSchemaIds: Seq[String]
  ): Validation[String, Unit] = {
    if (expectedSchemaIds.nonEmpty) {
      val isValidSchema = credentialSchemas match {
        case Some(schema: CredentialSchema)           => expectedSchemaIds.contains(schema.id)
        case Some(schemaList: List[CredentialSchema]) => expectedSchemaIds.intersect(schemaList.map(_.id)).nonEmpty
        case _                                        => false
      }
      if (!isValidSchema) {
        Validation.fail(s"SchemaId expected =$expectedSchemaIds actual found =$credentialSchemas")
      } else Validation.unit
    } else Validation.unit

  }

  def validateIsTrustedIssuer(
      credentialIssuer: String | CredentialIssuer,
      trustedIssuers: Seq[String]
  ): Validation[String, Unit] = {
    if (trustedIssuers.nonEmpty) {
      val isValidIssuer = credentialIssuer match
        case issuer: String           => trustedIssuers.contains(issuer)
        case issuer: CredentialIssuer => trustedIssuers.contains(issuer.id)
      if (!isValidIssuer) {
        Validation.fail(s"TrustedIssuers = ${trustedIssuers.mkString(",")} actual issuer = $credentialIssuer")
      } else Validation.unit
    } else Validation.unit

  }

  def validateNonce(
      decodedJwtPresentation: JwtPresentationPayload,
      nonce: Option[String]
  ): Validation[String, Unit] = {
    if (nonce != decodedJwtPresentation.maybeNonce) {
      Validation.fail(s"Challenge/Nonce dont match nonce=$nonce exp=${decodedJwtPresentation.maybeNonce}")
    } else Validation.unit
  }
  def validateAudience(
      decodedJwtPresentation: JwtPresentationPayload,
      domain: Option[String]
  ): Validation[String, Unit] = {
    if (!domain.forall(domain => decodedJwtPresentation.aud.contains(domain))) {
      Validation.fail(s"domain/Audience dont match domain=$domain, exp=${decodedJwtPresentation.aud}")
    } else Validation.unit
  }

  def verifyHolderBinding(jwt: JWT): Validation[String, Unit] = {

    def validateCredentialSubjectId(
        vcList: IndexedSeq[VerifiableCredentialPayload],
        iss: String
    ): Validation[String, Unit] = {
      ZValidation
        .validateAll(
          vcList.map {
            case (w3cVerifiableCredentialPayload: W3cVerifiableCredentialPayload) =>
              val mayBeSubjectDid = w3cVerifiableCredentialPayload.payload.credentialSubject
                .get(zio.json.ast.JsonCursor.field("id").isString)
                .map(_.value)
                .toOption
              if (mayBeSubjectDid.contains(iss)) {
                Validation.unit
              } else
                Validation.fail(
                  s"holder DID ${iss} that signed the presentation must match the credentialSubject did ${mayBeSubjectDid}  in each of the attached credentials"
                )

            case (jwtVerifiableCredentialPayload: JwtVerifiableCredentialPayload) =>
              for {
                jwtCredentialPayload <- Validation
                  .fromTry(decodeJwt[JwtCredentialPayload](jwtVerifiableCredentialPayload.jwt))
                  .mapError(_.toString)
                mayBeSubjectDid = jwtCredentialPayload.maybeSub
                x <-
                  if (mayBeSubjectDid.contains(iss)) {
                    Validation.unit
                  } else
                    Validation.fail(
                      s"holder DID ${iss} that signed the presentation must match the credentialSubject did  ${mayBeSubjectDid}  in each of the attached credentials"
                    )
              } yield x
          }
        )
        .map(_ => ())
    }
    for {
      jwtPresentationPayload <- Validation
        .fromTry(decodeJwt[JwtPresentationPayload](jwt))
        .mapError(_.toString)
      result <- validateCredentialSubjectId(jwtPresentationPayload.vp.verifiableCredential, jwtPresentationPayload.iss)
    } yield result
  }

  def verifyDates(jwt: JWT, leeway: TemporalAmount)(implicit clock: Clock): Validation[String, Unit] = {
    val now = clock.instant()
    def validateNbfNotAfterExp(maybeNbf: Option[Instant], maybeExp: Option[Instant]): Validation[String, Unit] = {
      val maybeResult =
        for {
          nbf <- maybeNbf
          exp <- maybeExp
        } yield {
          if (nbf.isAfter(exp))
            Validation.fail(s"Credential cannot expire before being in effect. nbf=$nbf exp=$exp")
          else Validation.unit
        }
      maybeResult.getOrElse(Validation.unit)
    }

    def validateNbf(maybeNbf: Option[Instant]): Validation[String, Unit] = {
      maybeNbf
        .map(nbf =>
          if (now.isBefore(nbf.minus(leeway)))
            Validation.fail(s"Credential is not yet in effect. now=$now nbf=$nbf leeway=$leeway")
          else Validation.unit
        )
        .getOrElse(Validation.unit)
    }

    def validateExp(maybeExp: Option[Instant]): Validation[String, Unit] = {
      maybeExp
        .map(exp =>
          if (now.isAfter(exp.plus(leeway)))
            Validation.fail(s"Verifiable Presentation has expired. now=$now exp=$exp leeway=$leeway")
          else Validation.unit
        )
        .getOrElse(Validation.unit)
    }

    for {
      jwtCredentialPayload <- Validation
        .fromTry(decodeJwt[JwtPresentationPayload](jwt))
        .mapError(_.toString)
      maybeNbf = jwtCredentialPayload.maybeNbf
      maybeExp = jwtCredentialPayload.maybeExp
      result <- Validation.validateWith(
        validateNbfNotAfterExp(maybeNbf, maybeExp),
        validateNbf(maybeNbf),
        validateExp(maybeExp)
      )((l, _, _) => l)
    } yield result
  }

  /** Defines what to verify in a jwt presentation */
  case class PresentationVerificationOptions(
      verifySignature: Boolean = true,
      verifyDates: Boolean = false,
      verifyHoldersBinding: Boolean = false,
      leeway: TemporalAmount = Duration.Zero,
      maybeCredentialOptions: Option[CredentialVerification.CredentialVerificationOptions] = None,
      maybeProofPurpose: Option[VerificationRelationship] = None
  )

  def verify(jwt: JWT, options: PresentationVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[List[String], Validation[String, Unit]] = {
    for {
      signatureValidation <-
        if (options.verifySignature) then
          validateEncodedJWT(jwt, options.maybeProofPurpose)(didResolver).mapError(List(_))
        else ZIO.succeed(Validation.unit)
      dateVerification <- ZIO.succeed(
        if (options.verifyDates) then verifyDates(jwt, options.leeway)(clock) else Validation.unit
      )
      verifyHoldersBinding <- ZIO.succeed(
        if (options.verifyHoldersBinding) then verifyHolderBinding(jwt) else Validation.unit
      )
      credentialVerification <-
        options.maybeCredentialOptions
          .map(credentialOptions =>
            validateEnclosedCredentials(jwt, credentialOptions)(didResolver, uriResolver)(clock)
          )
          .getOrElse(ZIO.succeed(Validation.unit))
    } yield Validation.validateWith(
      signatureValidation,
      dateVerification,
      credentialVerification,
      verifyHoldersBinding
    )((a, _, _, _) => a)
  }
}
