package org.hyperledger.identus.pollux.vc.jwt

import org.hyperledger.identus.castor.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.http.UriResolver
import pdi.jwt.{JwtOptions, JwtZIOJson}
import zio.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}
import zio.prelude.*

import java.security.PublicKey
import java.time.{Clock, Instant}
import java.time.temporal.TemporalAmount
import scala.util.Try

sealed trait VerifiablePresentationPayload

object VerifiablePresentationPayload {
  given JsonDecoder[VerifiablePresentationPayload] = JsonDecoder[Json].mapOrFail { json =>
    json
      .as[JwtVerifiablePresentationPayload]
      .orElse(json.as[W3cVerifiablePresentationPayload])
  }
}

case class W3cVerifiablePresentationPayload(payload: W3cPresentationPayload, proof: JwtProof)
    extends Verifiable(proof),
      VerifiablePresentationPayload

object W3cVerifiablePresentationPayload {
  given JsonDecoder[W3cVerifiablePresentationPayload] = JsonDecoder[Json].mapOrFail { json =>
    for {
      payload <- json.as[W3cPresentationPayload]
      proof <- json.get(JsonCursor.field("proof")).flatMap(_.as[JwtProof])
    } yield W3cVerifiablePresentationPayload(payload, proof)
  }
}

case class JwtVerifiablePresentationPayload(jwt: JWT) extends VerifiablePresentationPayload

object JwtVerifiablePresentationPayload {
  given JsonDecoder[JwtVerifiablePresentationPayload] =
    JsonDecoder.string.map(s => JwtVerifiablePresentationPayload(JWT(s)))
}

sealed trait PresentationPayload(
    `@context`: IndexedSeq[String],
    `type`: IndexedSeq[String],
    verifiableCredential: IndexedSeq[VerifiableCredentialPayload],
    iss: String,
    maybeNbf: Option[Instant],
    aud: IndexedSeq[String],
    maybeExp: Option[Instant],
    maybeJti: Option[String],
    maybeNonce: Option[String]
) {
  def toJwtPresentationPayload: JwtPresentationPayload =
    JwtPresentationPayload(
      iss = iss,
      vp = JwtVp(
        `@context` = `@context`,
        `type` = `type`,
        verifiableCredential = verifiableCredential
      ),
      maybeNbf = maybeNbf,
      aud = aud,
      maybeExp = maybeExp,
      maybeJti = maybeJti,
      maybeNonce = maybeNonce
    )

  def toW3CPresentationPayload: W3cPresentationPayload =
    W3cPresentationPayload(
      `@context` = `@context`.distinct,
      maybeId = maybeJti,
      `type` = `type`.distinct,
      verifiableCredential = verifiableCredential,
      holder = iss,
      verifier = aud,
      maybeIssuanceDate = maybeNbf,
      maybeExpirationDate = maybeExp,
      maybeNonce = maybeNonce
    )
}

case class W3cPresentationPayload(
    `@context`: IndexedSeq[String],
    maybeId: Option[String],
    `type`: IndexedSeq[String],
    verifiableCredential: IndexedSeq[VerifiableCredentialPayload],
    holder: String,
    verifier: IndexedSeq[String],
    maybeIssuanceDate: Option[Instant],
    maybeExpirationDate: Option[Instant],

    /** Not part of W3C Presentation but included to preserve in case of conversion from JWT. */
    maybeNonce: Option[String] = Option.empty
) extends PresentationPayload(
      `@context` = `@context`.distinct,
      `type` = `type`.distinct,
      maybeJti = maybeId,
      verifiableCredential = verifiableCredential,
      aud = verifier,
      iss = holder,
      maybeNbf = maybeIssuanceDate,
      maybeExp = maybeExpirationDate,
      maybeNonce = maybeNonce
    )

object W3cPresentationPayload {
  import JsonEncoders.given
  private case class Json_W3cPresentationPayload(
      `@context`: String | IndexedSeq[String],
      `type`: String | IndexedSeq[String],
      id: Option[String],
      verifiableCredential: IndexedSeq[VerifiableCredentialPayload],
      holder: String,
      verifier: String | IndexedSeq[String],
      issuanceDate: Option[Instant],
      expirationDate: Option[Instant]
  )

  private given JsonEncoder[Json_W3cPresentationPayload] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_W3cPresentationPayload] = DeriveJsonDecoder.gen

  given JsonEncoder[W3cPresentationPayload] = JsonEncoder[Json_W3cPresentationPayload].contramap { payload =>
    Json_W3cPresentationPayload(
      payload.`@context`,
      payload.`type`,
      payload.maybeId,
      payload.verifiableCredential,
      payload.holder,
      payload.verifier,
      payload.maybeIssuanceDate,
      payload.maybeExpirationDate
    )
  }
  given JsonDecoder[W3cPresentationPayload] = JsonDecoder[Json_W3cPresentationPayload].map { payload =>
    W3cPresentationPayload(
      payload.`@context` match
        case str: String             => IndexedSeq(str)
        case set: IndexedSeq[String] => set
      ,
      payload.id,
      payload.`type` match
        case str: String             => IndexedSeq(str)
        case set: IndexedSeq[String] => set
      ,
      payload.verifiableCredential match
        case str: VerifiableCredentialPayload             => IndexedSeq(str)
        case set: IndexedSeq[VerifiableCredentialPayload] => set
      ,
      payload.holder,
      payload.verifier match
        case str: String             => IndexedSeq(str)
        case set: IndexedSeq[String] => set
      ,
      payload.issuanceDate,
      payload.expirationDate,
      None
    )
  }
}

case class JwtVp(
    `@context`: IndexedSeq[String],
    `type`: IndexedSeq[String],
    verifiableCredential: IndexedSeq[VerifiableCredentialPayload]
)

object JwtVp {
  private case class Json_JwtVp(
      `@context`: IndexedSeq[String],
      `type`: IndexedSeq[String],
      verifiableCredential: IndexedSeq[VerifiableCredentialPayload]
  )

  private given JsonEncoder[Json_JwtVp] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_JwtVp] = JsonDecoder[Json].mapOrFail { json =>
    for {
      context <- json
        .get(JsonCursor.field("@context"))
        .flatMap(ctx => ctx.as[String].map(IndexedSeq(_)).orElse(ctx.as[IndexedSeq[String]]))
      typ <- json
        .get(JsonCursor.field("type"))
        .flatMap(ctx => ctx.as[String].map(IndexedSeq(_)).orElse(ctx.as[IndexedSeq[String]]))
      vcp <- json
        .get(JsonCursor.field("verifiableCredential"))
        .flatMap(ctx =>
          ctx
            .as[VerifiableCredentialPayload]
            .map(IndexedSeq(_))
            .orElse(ctx.as[IndexedSeq[VerifiableCredentialPayload]])
        )
        .orElse(Right(IndexedSeq.empty[VerifiableCredentialPayload]))
    } yield Json_JwtVp(context, typ, vcp)
  }

  given JsonEncoder[JwtVp] = JsonEncoder[Json_JwtVp].contramap { payload =>
    Json_JwtVp(
      payload.`@context`,
      payload.`type`,
      payload.verifiableCredential
    )
  }
  given JsonDecoder[JwtVp] = JsonDecoder[Json_JwtVp].map { payload =>
    JwtVp(payload.`@context`, payload.`type`, payload.verifiableCredential)
  }
}

case class JwtPresentationPayload(
    iss: String,
    vp: JwtVp,
    maybeNbf: Option[Instant],
    aud: IndexedSeq[String],
    maybeExp: Option[Instant],
    maybeJti: Option[String],
    maybeNonce: Option[String]
) extends PresentationPayload(
      iss = iss,
      `@context` = vp.`@context`,
      `type` = vp.`type`,
      verifiableCredential = vp.verifiableCredential,
      maybeNbf = maybeNbf,
      aud = aud,
      maybeExp = maybeExp,
      maybeJti = maybeJti,
      maybeNonce = maybeNonce
    )

object JwtPresentationPayload {
  import JsonEncoders.given
  private case class Json_JwtPresentationPayload(
      iss: String,
      vp: JwtVp,
      nbf: Option[Instant],
      aud: String | IndexedSeq[String] = IndexedSeq.empty,
      exp: Option[Instant],
      jti: Option[String],
      nonce: Option[String]
  )

  private given JsonEncoder[Json_JwtPresentationPayload] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_JwtPresentationPayload] = DeriveJsonDecoder.gen

  given JsonEncoder[JwtPresentationPayload] = JsonEncoder[Json_JwtPresentationPayload].contramap { payload =>
    Json_JwtPresentationPayload(
      payload.iss,
      payload.vp,
      payload.maybeNbf,
      payload.aud,
      payload.maybeExp,
      payload.maybeJti,
      payload.maybeNonce
    )
  }
  given JsonDecoder[JwtPresentationPayload] = JsonDecoder[Json_JwtPresentationPayload].map { payload =>
    JwtPresentationPayload(
      payload.iss,
      payload.vp,
      payload.nbf,
      payload.aud match
        case str: String             => IndexedSeq(str)
        case set: IndexedSeq[String] => set.distinct
      ,
      payload.exp,
      payload.jti,
      payload.nonce
    )
  }
}

//FIXME THIS WILL NOT WORK like that
case class AnoncredVp(
    `@context`: IndexedSeq[String],
    `type`: IndexedSeq[String],
    verifiableCredential: IndexedSeq[VerifiableCredentialPayload]
)
case class AnoncredPresentationPayload(
    iss: String,
    vp: JwtVp,
    maybeNbf: Option[Instant],
    aud: IndexedSeq[String],
    maybeExp: Option[Instant],
    maybeJti: Option[String],
    maybeNonce: Option[String]
) extends PresentationPayload(
      iss = iss,
      `@context` = vp.`@context`,
      `type` = vp.`type`,
      verifiableCredential = vp.verifiableCredential,
      maybeNbf = maybeNbf,
      aud = aud,
      maybeExp = maybeExp,
      maybeJti = maybeJti,
      maybeNonce = maybeNonce
    )

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

  def decodeJwt[A](jwt: JWT)(using decoder: JsonDecoder[A]): Try[A] = {
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
                .get(JsonCursor.field("id").isString)
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

  /** Defines what to verify in a jwt presentation
    * @param verifySignature
    *   verifies signature using the resolved did.
    * @param verifyDates
    *   verifies issuance and expiration dates.
    * @param leeway
    *   defines the duration we should subtract from issuance date and add to expiration dates.
    * @param maybeCredentialOptions
    *   defines what to verify in the jwt credentials. If empty, credentials verification will be ignored.
    * @param maybeProofPurpose
    *   specifies the which type of public key to use in the resolved DidDocument. If empty, we will validate against
    *   all public key.
    */
  case class PresentationVerificationOptions(
      verifySignature: Boolean = true,
      verifyDates: Boolean = false,
      verifyHoldersBinding: Boolean = false,
      leeway: TemporalAmount = Duration.Zero,
      maybeCredentialOptions: Option[CredentialVerification.CredentialVerificationOptions] = None,
      maybeProofPurpose: Option[VerificationRelationship] = None
  )

  /** Verifies a jwt presentation.
    * @param jwt
    *   presentation to verify.
    * @param options
    *   defines what to verify.
    * @param didResolver
    *   is used to resolve the did.
    * @param clock
    *   is used to get current time.
    * @return
    *   the result of the validation.
    */
  def verify(jwt: JWT, options: PresentationVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[List[String], Validation[String, Unit]] = {
    // TODO: verify revocation status of credentials inside the presentation
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
