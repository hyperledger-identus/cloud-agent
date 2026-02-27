package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jwt.SignedJWT
import org.hyperledger.identus.castor.core.model.did.{DID, VerificationRelationship}
import org.hyperledger.identus.pollux.vc.jwt.revocation.BitString
import org.hyperledger.identus.shared.crypto.KmpSecp256k1KeyOps
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.json.JsonOps.*
import org.hyperledger.identus.shared.utils.Base64Utils
import pdi.jwt.*
import zio.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}
import zio.json.internal.Write
import zio.prelude.*

import java.security.PublicKey
import java.time.{Clock, Instant, OffsetDateTime, ZoneId}
import java.time.temporal.TemporalAmount
import scala.util.{Failure, Try}

case class Issuer(did: DID, signer: Signer, publicKey: PublicKey)

sealed trait VerifiableCredentialPayload

object VerifiableCredentialPayload {
  given JsonEncoder[VerifiableCredentialPayload] =
    (a: VerifiableCredentialPayload, indent: Option[Int], out: Write) =>
      a match
        case p: W3cVerifiableCredentialPayload =>
          JsonEncoder[W3cVerifiableCredentialPayload].unsafeEncode(p, indent, out)
        case p: JwtVerifiableCredentialPayload =>
          JsonEncoder[JwtVerifiableCredentialPayload].unsafeEncode(p, indent, out)

  given JsonDecoder[VerifiableCredentialPayload] = JsonDecoder[Json].mapOrFail { json =>
    json
      .as[JwtVerifiableCredentialPayload]
      .orElse(json.as[W3cVerifiableCredentialPayload])
  }
}

case class W3cVerifiableCredentialPayload(payload: W3cCredentialPayload, proof: JwtProof)
    extends Verifiable(proof),
      VerifiableCredentialPayload

object W3cVerifiableCredentialPayload {
  given JsonEncoder[W3cVerifiableCredentialPayload] = JsonEncoder[Json].contramap { payload =>
    (for {
      jsonObject <- payload.toJsonAST.flatMap(_.asObject.toRight("Payload's json representation is not an object"))
      payload <- payload.proof.toJsonAST.map(p => jsonObject.add("proof", p))
    } yield payload).getOrElse(UnexpectedCodeExecutionPath)
  }
  given JsonDecoder[W3cVerifiableCredentialPayload] = JsonDecoder[Json].mapOrFail { json =>
    for {
      payload <- json.as[W3cCredentialPayload]
      proof <- json.get(JsonCursor.field("proof")).flatMap(_.as[JwtProof])
    } yield W3cVerifiableCredentialPayload(payload, proof)
  }
}

case class JwtVerifiableCredentialPayload(jwt: JWT) extends VerifiableCredentialPayload

object JwtVerifiableCredentialPayload {
  given JsonEncoder[JwtVerifiableCredentialPayload] = JsonEncoder.string.contramap(_.jwt.value)
  given JsonDecoder[JwtVerifiableCredentialPayload] =
    JsonDecoder[String].map(s => JwtVerifiableCredentialPayload(JWT(s)))
}

enum StatusPurpose {
  case Revocation
  case Suspension
}

object StatusPurpose {
  given JsonEncoder[StatusPurpose] = DeriveJsonEncoder.gen
  given JsonDecoder[StatusPurpose] = DeriveJsonDecoder.gen
}

case class CredentialStatus(
    id: String,
    `type`: String,
    statusPurpose: StatusPurpose,
    statusListIndex: Int,
    statusListCredential: String
)

object CredentialStatus {
  given JsonEncoder[CredentialStatus] = DeriveJsonEncoder.gen
  given JsonDecoder[CredentialStatus] = DeriveJsonDecoder.gen
}

case class RefreshService(
    id: String,
    `type`: String
)

object RefreshService {
  given JsonEncoder[RefreshService] = DeriveJsonEncoder.gen
  given JsonDecoder[RefreshService] = DeriveJsonDecoder.gen
}

//TODO: refactor to use the new CredentialSchemaRef
case class CredentialSchema(
    id: String,
    `type`: String
)

object CredentialSchema {
  given JsonEncoder[CredentialSchema] = DeriveJsonEncoder.gen
  given JsonDecoder[CredentialSchema] = DeriveJsonDecoder.gen
}

case class CredentialIssuer(
    id: String,
    `type`: String
)

object CredentialIssuer {
  given JsonEncoder[CredentialIssuer] = DeriveJsonEncoder.gen
  given JsonDecoder[CredentialIssuer] = DeriveJsonDecoder.gen
}

sealed trait CredentialPayload {
  def maybeSub: Option[String]

  def `@context`: Set[String]

  def `type`: Set[String]

  def maybeJti: Option[String]

  def nbf: Instant

  def aud: Set[String]

  def maybeExp: Option[Instant]

  def maybeValidFrom: Option[Instant]

  def maybeValidUntil: Option[Instant]

  def issuer: String | CredentialIssuer

  def maybeCredentialStatus: Option[CredentialStatus | List[CredentialStatus]]

  def maybeRefreshService: Option[RefreshService]

  def maybeEvidence: Option[Json]

  def maybeTermsOfUse: Option[Json]

  def maybeCredentialSchema: Option[CredentialSchema | List[CredentialSchema]]

  def credentialSubject: Json

  def toJwtCredentialPayload: JwtCredentialPayload =
    JwtCredentialPayload(
      iss = issuer match {
        case string: String                     => string
        case credentialIssuer: CredentialIssuer => credentialIssuer.id
      },
      maybeSub = maybeSub,
      vc = JwtVc(
        `@context` = `@context`,
        `type` = `type`,
        maybeCredentialSchema = maybeCredentialSchema,
        credentialSubject = credentialSubject,
        maybeCredentialStatus = maybeCredentialStatus,
        maybeRefreshService = maybeRefreshService,
        maybeEvidence = maybeEvidence,
        maybeTermsOfUse = maybeTermsOfUse,
        maybeValidFrom = maybeValidFrom,
        maybeValidUntil = maybeValidUntil,
        maybeIssuer = Some(issuer),
      ),
      nbf = nbf,
      aud = aud,
      maybeExp = maybeExp,
      maybeJti = maybeJti
    )

  def toW3CCredentialPayload: W3cCredentialPayload =
    W3cCredentialPayload(
      `@context` = `@context`,
      maybeId = maybeJti,
      `type` = `type`,
      issuer = issuer,
      issuanceDate = nbf,
      maybeExpirationDate = maybeExp,
      maybeCredentialSchema = maybeCredentialSchema,
      credentialSubject = credentialSubject,
      maybeCredentialStatus = maybeCredentialStatus,
      maybeRefreshService = maybeRefreshService,
      maybeEvidence = maybeEvidence,
      maybeTermsOfUse = maybeTermsOfUse,
      aud = aud,
      maybeValidFrom = maybeValidFrom,
      maybeValidUntil = maybeValidUntil
    )
}

case class JwtVc(
    `@context`: Set[String],
    `type`: Set[String],
    maybeCredentialSchema: Option[CredentialSchema | List[CredentialSchema]],
    credentialSubject: Json,
    maybeValidFrom: Option[Instant],
    maybeValidUntil: Option[Instant],
    maybeIssuer: Option[String | CredentialIssuer],
    maybeCredentialStatus: Option[CredentialStatus | List[CredentialStatus]],
    maybeRefreshService: Option[RefreshService],
    maybeEvidence: Option[Json],
    maybeTermsOfUse: Option[Json]
)

object JwtVc {
  import JsonEncoders.given

  private case class Json_JwtVc(
      `@context`: String | Set[String],
      `type`: String | Set[String],
      credentialSchema: Option[CredentialSchema | List[CredentialSchema]],
      credentialSubject: Json,
      credentialStatus: Option[CredentialStatus | List[CredentialStatus]],
      refreshService: Option[RefreshService],
      evidence: Option[Json],
      termsOfUse: Option[Json],
      validFrom: Option[Instant],
      validUntil: Option[Instant],
      issuer: Option[String | CredentialIssuer]
  )

  private given JsonEncoder[Json_JwtVc] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_JwtVc] = DeriveJsonDecoder.gen

  given JsonEncoder[JwtVc] = JsonEncoder[Json_JwtVc].contramap { vc =>
    Json_JwtVc(
      vc.`@context`,
      vc.`type`,
      vc.maybeCredentialSchema,
      vc.credentialSubject,
      vc.maybeCredentialStatus,
      vc.maybeRefreshService,
      vc.maybeEvidence,
      vc.maybeTermsOfUse,
      vc.maybeValidFrom,
      vc.maybeValidUntil,
      vc.maybeIssuer
    )
  }

  given JsonDecoder[JwtVc] = JsonDecoder[Json_JwtVc].map { payload =>
    JwtVc(
      payload.`@context` match
        case str: String      => Set(str)
        case set: Set[String] => set
      ,
      payload.`type` match
        case str: String      => Set(str)
        case set: Set[String] => set
      ,
      payload.credentialSchema,
      payload.credentialSubject,
      payload.validFrom,
      payload.validUntil,
      payload.issuer,
      payload.credentialStatus,
      payload.refreshService,
      payload.evidence,
      payload.termsOfUse
    )
  }
}

case class JwtCredentialPayload(
    iss: String,
    override val maybeSub: Option[String],
    vc: JwtVc,
    override val nbf: Instant,
    override val aud: Set[String],
    override val maybeExp: Option[Instant],
    override val maybeJti: Option[String]
) extends CredentialPayload {
  override val `@context` = vc.`@context`
  override val `type` = vc.`type`
  override val maybeCredentialStatus = vc.maybeCredentialStatus
  override val maybeRefreshService = vc.maybeRefreshService
  override val maybeEvidence = vc.maybeEvidence
  override val maybeTermsOfUse = vc.maybeTermsOfUse
  override val maybeCredentialSchema = vc.maybeCredentialSchema
  override val credentialSubject = vc.credentialSubject
  override val maybeValidFrom = vc.maybeValidFrom
  override val maybeValidUntil = vc.maybeValidUntil
  override val issuer = vc.maybeIssuer.getOrElse(iss)
}

object JwtCredentialPayload {
  import JsonEncoders.given

  private case class Json_JwtCredentialPayload(
      iss: String,
      sub: Option[String],
      vc: JwtVc,
      nbf: Instant,
      aud: String | Set[String] = Set.empty,
      exp: Option[Instant],
      jti: Option[String]
  )

  private given JsonEncoder[Json_JwtCredentialPayload] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_JwtCredentialPayload] = DeriveJsonDecoder.gen

  given JsonEncoder[JwtCredentialPayload] = JsonEncoder[Json_JwtCredentialPayload].contramap { payload =>
    Json_JwtCredentialPayload(
      payload.iss,
      payload.maybeSub,
      payload.vc,
      payload.nbf,
      payload.aud,
      payload.maybeExp,
      payload.maybeJti
    )
  }

  given JsonDecoder[JwtCredentialPayload] = JsonDecoder[Json_JwtCredentialPayload].map { payload =>
    JwtCredentialPayload(
      payload.iss,
      payload.sub,
      payload.vc,
      payload.nbf,
      payload.aud match
        case str: String      => Set(str)
        case set: Set[String] => set
      ,
      payload.exp,
      payload.jti
    )
  }
}

case class W3cCredentialPayload(
    override val `@context`: Set[String],
    override val `type`: Set[String],
    maybeId: Option[String],
    issuer: String | CredentialIssuer,
    issuanceDate: Instant,
    maybeExpirationDate: Option[Instant],
    override val maybeCredentialSchema: Option[CredentialSchema | List[CredentialSchema]],
    override val credentialSubject: Json,
    override val maybeCredentialStatus: Option[CredentialStatus | List[CredentialStatus]],
    override val maybeRefreshService: Option[RefreshService],
    override val maybeEvidence: Option[Json],
    override val maybeTermsOfUse: Option[Json],
    override val aud: Set[String] = Set.empty,
    override val maybeValidFrom: Option[Instant],
    override val maybeValidUntil: Option[Instant]
) extends CredentialPayload {
  override val maybeSub = credentialSubject.get(JsonCursor.field("id").isString).map(_.value).toOption
  override val maybeJti = maybeId
  override val nbf = issuanceDate
  override val maybeExp = maybeExpirationDate
}

object W3cCredentialPayload {
  import JsonEncoders.given
  private case class Json_W3cCredentialPayload(
      `@context`: String | Set[String],
      `type`: String | Set[String],
      id: Option[String],
      issuer: String | CredentialIssuer,
      issuanceDate: Instant,
      expirationDate: Option[Instant],
      validFrom: Option[Instant],
      validUntil: Option[Instant],
      credentialSchema: Option[CredentialSchema | List[CredentialSchema]],
      credentialSubject: Json,
      credentialStatus: Option[CredentialStatus | List[CredentialStatus]],
      refreshService: Option[RefreshService],
      evidence: Option[Json],
      termsOfUse: Option[Json]
  )

  private given JsonEncoder[Json_W3cCredentialPayload] = DeriveJsonEncoder.gen
  private given JsonDecoder[Json_W3cCredentialPayload] = DeriveJsonDecoder.gen

  given JsonEncoder[W3cCredentialPayload] = JsonEncoder[Json_W3cCredentialPayload].contramap { payload =>
    Json_W3cCredentialPayload(
      payload.`@context`,
      payload.`type`,
      payload.maybeId,
      payload.issuer,
      payload.issuanceDate,
      payload.maybeExpirationDate,
      payload.maybeValidFrom,
      payload.maybeValidUntil,
      payload.maybeCredentialSchema,
      payload.credentialSubject,
      payload.maybeCredentialStatus,
      payload.maybeRefreshService,
      payload.maybeEvidence,
      payload.maybeTermsOfUse
    )
  }
  given JsonDecoder[W3cCredentialPayload] = JsonDecoder[Json_W3cCredentialPayload].map { payload =>
    W3cCredentialPayload(
      payload.`@context` match
        case str: String      => Set(str)
        case set: Set[String] => set
      ,
      payload.`type` match
        case str: String      => Set(str)
        case set: Set[String] => set
      ,
      payload.id,
      payload.issuer,
      payload.issuanceDate,
      payload.expirationDate,
      payload.credentialSchema,
      payload.credentialSubject,
      payload.credentialStatus,
      payload.refreshService,
      payload.evidence,
      payload.termsOfUse,
      Set.empty,
      payload.validFrom,
      payload.validUntil,
    )
  }
}

object CredentialVerification {

  def validateValidFromNotAfterValidUntil(
      maybeValidFrom: Option[Instant],
      maybeValidUntil: Option[Instant],
      validFromName: String,
      validUntilName: String
  ): Validation[String, Unit] = {
    (maybeValidFrom, maybeValidUntil)
      .mapN((validFrom, validUntil) =>
        if (validFrom.isAfter(validUntil))
          Validation.fail(
            s"Credential cannot expire before being in effect. $validFromName=$validFrom $validUntilName=$validUntil"
          )
        else Validation.unit
      )
      .getOrElse(Validation.unit)
  }

  private def validateValidFrom(
      maybeValidFrom: Option[Instant],
      now: Instant,
      leeway: TemporalAmount,
      validFromName: String,
  ): Validation[String, Unit] = {
    maybeValidFrom
      .map(validFrom =>
        if (now.isBefore(validFrom.minus(leeway)))
          Validation.fail(s"Credential is not yet in effect. now=$now $validFromName=$validFrom leeway=$leeway")
        else Validation.unit
      )
      .getOrElse(Validation.unit)
  }

  private def validateValidUntil(
      maybeValidUntil: Option[Instant],
      now: Instant,
      leeway: TemporalAmount,
      validUntilName: String,
  ): Validation[String, Unit] = {
    maybeValidUntil
      .map(validUntil =>
        if (now.isAfter(validUntil.plus(leeway)))
          Validation.fail(s"Credential has expired. now=$now $validUntilName=$validUntil leeway=$leeway")
        else Validation.unit
      )
      .getOrElse(Validation.unit)
  }

  def verifyDates(
      maybeValidFrom: Option[Instant],
      maybeValidUntil: Option[Instant],
      leeway: TemporalAmount,
      validFromName: String,
      validUntilName: String
  )(implicit
      clock: Clock
  ): Validation[String, Unit] = {
    val now = clock.instant()
    Validation.validateWith(
      validateValidFromNotAfterValidUntil(maybeValidFrom, maybeValidUntil, validFromName, validUntilName),
      validateValidFrom(maybeValidFrom, now, leeway, validFromName),
      validateValidUntil(maybeValidUntil, now, leeway, validUntilName)
    )((l, _, _) => l)
  }

  /** Defines what to verify in the jwt credentials.
    *
    * @param verifySignature
    *   verifies signature using the resolved did.
    * @param verifyDates
    *   verifies issuance and expiration dates.
    * @param leeway
    *   defines the duration we should subtract from issuance date and add to expiration dates.
    * @param maybeProofPurpose
    *   specifies the which type of public key to use in the resolved DidDocument. If empty, we will validate against
    *   all public key.
    */
  case class CredentialVerificationOptions(
      verifySignature: Boolean = true,
      verifyDates: Boolean = false,
      leeway: TemporalAmount = Duration.Zero,
      maybeProofPurpose: Option[VerificationRelationship] = None
  )

  /** Verifies a jwt credential.
    *
    * @param jwt
    *   credential to verify.
    * @param options
    *   defines what to verify.
    * @param didResolver
    *   is used to resolve the did.
    * @param clock
    *   is used to get current time.
    * @return
    *   the result of the validation.
    */
  def verify(verifiableCredentialPayload: VerifiableCredentialPayload, options: CredentialVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[String, Validation[String, Unit]] = {
    verifiableCredentialPayload match {
      case w3cVerifiableCredentialPayload: W3cVerifiableCredentialPayload =>
        W3CCredential.verify(w3cVerifiableCredentialPayload, options)(didResolver, uriResolver)
      case jwtVerifiableCredentialPayload: JwtVerifiableCredentialPayload =>
        JwtCredential.verify(jwtVerifiableCredentialPayload, options)(didResolver, uriResolver)
    }
  }

  def verifyCredentialStatus(
      credentialStatus: CredentialStatus
  )(uriResolver: UriResolver): IO[String, Validation[String, Unit]] = {
    val res = for {
      statusListString <- uriResolver
        .resolve(credentialStatus.statusListCredential)
        .mapError(err => s"Could not resolve status list credential: $err")
      _ <- ZIO.logInfo("Credential status: " + credentialStatus)
      vcStatusListCredJson <- ZIO
        .fromEither(statusListString.fromJson[Json])
        .mapError(err => s"Could not parse status list credential as Json string: $err")
      statusListCredJsonWithoutProof = vcStatusListCredJson.removeField("proof")
      proof <- ZIO
        .fromEither(vcStatusListCredJson.get(JsonCursor.field("proof")).flatMap(_.as[Proof]))
        .mapError(err => s"Could not extract proof from status list credential: $err")

      // Verify proof
      verified <- proof match
        case EddsaJcs2022Proof(proofValue, verificationMethod, maybeCreated) =>
          val publicKeyMultiBaseEffect = uriResolver
            .resolve(verificationMethod)
            .mapError(_.toThrowable.getMessage)
            .flatMap { jsonResponse =>
              ZIO.fromEither(jsonResponse.fromJson[MultiKey])
            }

          for {
            publicKeyMultiBase <- publicKeyMultiBaseEffect
            verified <- EddsaJcs2022ProofGenerator
              .verifyProof(statusListCredJsonWithoutProof, proofValue, publicKeyMultiBase)
              .mapError(_.getMessage)
          } yield verified

        case EcdsaSecp256k1Signature2019Proof(jws, verificationMethod, _, _, _, _) =>
          val jwkEffect = uriResolver
            .resolve(verificationMethod)
            .mapError(_.toThrowable.getMessage)
            .flatMap { jsonResponse =>
              ZIO.fromEither(jsonResponse.fromJson[EcdsaSecp256k1VerificationKey2019].map(_.publicKeyJwk))
            }

          for {
            jwk <- jwkEffect
            x <- ZIO.fromOption(jwk.x).orElseFail("Missing x coordinate in public key")
            y <- ZIO.fromOption(jwk.y).orElseFail("Missing y coordinate in public key")
            _ <- jwk.crv.fold(ZIO.fail("Missing crv in public key")) { crv =>
              if crv != "secp256k1" then ZIO.fail(s"Curve must be secp256k1, got $crv")
              else ZIO.unit
            }
            xBytes = Base64Utils.decodeURL(x)
            yBytes = Base64Utils.decodeURL(y)
            ecPublicKey <- ZIO
              .fromTry(KmpSecp256k1KeyOps.publicKeyFromCoordinate(xBytes, yBytes))
              .map(_.toJavaPublicKey)
              .mapError(_.getMessage)
            verified <- EcdsaSecp256k1Signature2019ProofGenerator
              .verifyProof(statusListCredJsonWithoutProof, jws, ecPublicKey)
              .mapError(_.getMessage)
          } yield verified
      proofVerificationValidation =
        if (verified) Validation.unit else Validation.fail("Could not verify status list credential proof")

      // Check revocation status in the list by index
      encodedBitString <- ZIO.fromEither(
        vcStatusListCredJson
          .get(JsonCursor.field("credentialSubject").isObject.field("encodedList").isString)
          .map(_.value)
      )
      bitString <- BitString.valueOf(encodedBitString).mapError(_.message)
      isRevoked <- bitString.isRevoked(credentialStatus.statusListIndex).mapError(_.message)
      revocationValidation = if (isRevoked) Validation.fail("Credential is revoked") else Validation.unit

    } yield Validation.validateWith(proofVerificationValidation, revocationValidation)((a, _) => a)

    res
  }
}

object JwtCredential {

  def encodeJwt(payload: JwtCredentialPayload, issuer: Issuer): JWT =
    issuer.signer.encode(payload.toJsonAST.getOrElse(UnexpectedCodeExecutionPath))

  def decodeJwt(jwt: JWT, publicKey: PublicKey): Try[JwtCredentialPayload] = {
    val signedJWT = SignedJWT.parse(jwt.value)
    val verifier = JWTVerification.toECDSAVerifier(publicKey)

    val isSignatureValid = signedJWT.verify(verifier)

    if isSignatureValid then
      val claimsSet = signedJWT.getJWTClaimsSet.toString
      claimsSet.fromJson[JwtCredentialPayload].left.map(s => new RuntimeException(s)).toTry
    else Failure(Exception(s"Invalid JWT signature for: ${JWT.value}"))
  }

  def decodeJwt(jwt: JWT): IO[String, JwtCredentialPayload] = {
    val decodeJWT =
      ZIO.fromTry(JwtZIOJson.decodeRawAll(jwt.value, JwtOptions(false, false, false))).mapError(_.getMessage)

    val validatedDecodedClaim: IO[String, JwtCredentialPayload] =
      for {
        decodedJwtTask <- decodeJWT
        (_, claim, _) = decodedJwtTask
        decodedClaim <- ZIO.fromEither(claim.fromJson[JwtCredentialPayload])
      } yield decodedClaim

    validatedDecodedClaim
  }

  def validateEncodedJwt(jwt: JWT, publicKey: PublicKey): Boolean =
    val signedJWT = SignedJWT.parse(jwt.value)
    val verifier = JWTVerification.toECDSAVerifier(publicKey)
    signedJWT.verify(verifier)

  def validateEncodedJWT(
      jwt: JWT,
      proofPurpose: Option[VerificationRelationship] = None
  )(didResolver: DidResolver): IO[String, Validation[String, Unit]] = {
    JWTVerification.validateEncodedJwt(jwt, proofPurpose)(didResolver: DidResolver)(claim =>
      Validation.fromEither(claim.fromJson[JwtCredentialPayload])
    )(_.iss)
  }

  def validateIssuerJWT(
      jwt: JWT,
  )(didResolver: DidResolver): IO[String, Validation[String, DIDDocument]] = {
    JWTVerification.validateIssuer(jwt)(didResolver: DidResolver)(claim =>
      Validation.fromEither(claim.fromJson[JwtCredentialPayload])
    )(_.iss)
  }

  def validateExpiration(jwt: JWT, dateTime: OffsetDateTime): Validation[String, Unit] = {
    Validation
      .fromTry(
        JwtZIOJson(Clock.fixed(dateTime.toInstant, ZoneId.of(dateTime.getOffset.getId)))
          .decodeRawAll(jwt.value, JwtOptions(false, true, false))
      )
      .flatMap(_ => Validation.unit)
      .mapError(_.getMessage)
  }

  def validateNotBefore(jwt: JWT, dateTime: OffsetDateTime): Validation[String, Unit] = {
    Validation
      .fromTry(
        JwtZIOJson(Clock.fixed(dateTime.toInstant, ZoneId.of(dateTime.getOffset.getId)))
          .decodeRawAll(jwt.value, JwtOptions(false, false, true))
      )
      .flatMap(_ => Validation.unit)
      .mapError(_.getMessage)
  }

  def verifyDates(jwt: JWT, leeway: TemporalAmount)(implicit clock: Clock): Validation[String, Unit] = {
    val decodeJWT =
      Validation
        .fromTry(JwtZIOJson.decodeRaw(jwt.value, options = JwtOptions(false, false, false)))
        .mapError(_.getMessage)

    for {
      decodedJWT <- decodeJWT
      jwtCredentialPayload <- Validation.fromEither(decodedJWT.fromJson[JwtCredentialPayload])
      nbf = jwtCredentialPayload.nbf
      maybeExp = jwtCredentialPayload.maybeExp
      maybeValidFrom = jwtCredentialPayload.vc.maybeValidFrom
      maybeValidUntil = jwtCredentialPayload.vc.maybeValidUntil
      result <- Validation.validateWith(
        CredentialVerification.verifyDates(maybeValidFrom, maybeValidUntil, leeway, "validFrom", "validUntil")(clock),
        CredentialVerification.verifyDates(Some(nbf), maybeExp, leeway, "nbf", "exp")(clock)
      )((l, _) => l)
    } yield result
  }

  def verify(jwt: JwtVerifiableCredentialPayload, options: CredentialVerification.CredentialVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[String, Validation[String, Unit]] =
    verify(jwt.jwt, options)(didResolver, uriResolver)(clock)

  def verify(jwt: JWT, options: CredentialVerification.CredentialVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[String, Validation[String, Unit]] = {
    for {
      signatureValidation <-
        if (options.verifySignature) then validateEncodedJWT(jwt, options.maybeProofPurpose)(didResolver)
        else ZIO.succeed(Validation.unit)
      dateVerification <- ZIO.succeed(
        if (options.verifyDates) then verifyDates(jwt, options.leeway) else Validation.unit
      )
      revocationVerification <- verifyRevocationStatusJwt(jwt)(uriResolver)

    } yield Validation.validateWith(signatureValidation, dateVerification, revocationVerification)((a, _, _) => a)
  }

  def verifyRevocationStatusJwt(jwt: JWT)(uriResolver: UriResolver): IO[String, Validation[String, Unit]] = {
    val decodeJWT =
      ZIO
        .fromTry(JwtZIOJson.decodeRaw(jwt.value, options = JwtOptions(false, false, false)))
        .mapError(_.getMessage)

    val res = for {
      decodedJWT <- decodeJWT
      jwtCredentialPayload <- ZIO.fromEither(decodedJWT.fromJson[JwtCredentialPayload])
      credentialStatus = jwtCredentialPayload.vc.maybeCredentialStatus
        .map {
          {
            case status: CredentialStatus           => List(status)
            case statusList: List[CredentialStatus] => statusList
          }
        }
        .getOrElse(List.empty)
      results <- ZIO.collectAll(
        credentialStatus.map(status => CredentialVerification.verifyCredentialStatus(status)(uriResolver))
      )
      result = Validation.validateAll(results).flatMap(_ => Validation.unit)
    } yield result
    res
  }
}

object W3CCredential {

  def encodeW3C(payload: W3cCredentialPayload, issuer: Issuer): W3cVerifiableCredentialPayload = {
    W3cVerifiableCredentialPayload(
      payload = payload,
      proof = JwtProof(
        `type` = "JwtProof2020",
        jwt = issuer.signer.encode(payload.toJsonAST.getOrElse(UnexpectedCodeExecutionPath))
      )
    )
  }

  def toEncodedJwt(payload: W3cCredentialPayload, issuer: Issuer): JWT =
    JwtCredential.encodeJwt(payload.toJwtCredentialPayload, issuer)

  def toJsonWithEmbeddedProof(payload: W3cCredentialPayload, issuer: Issuer): Task[Json] = {
    val jsonCred = payload.toJsonAST.toOption.flatMap(_.asObject).getOrElse(UnexpectedCodeExecutionPath)

    for {
      proof <- issuer.signer.generateProofForJson(jsonCred, issuer.publicKey)
      jsonProof <- proof match
        case b: EcdsaSecp256k1Signature2019Proof => ZIO.succeed(b.toJsonAST.getOrElse(UnexpectedCodeExecutionPath))
        case c: EddsaJcs2022Proof                => ZIO.succeed(c.toJsonAST.getOrElse(UnexpectedCodeExecutionPath))
      verifiableCredentialWithProof = jsonCred.add("proof", jsonProof)
    } yield verifiableCredentialWithProof

  }

  def validateW3C(
      payload: W3cVerifiableCredentialPayload,
      proofPurpose: Option[VerificationRelationship] = None
  )(didResolver: DidResolver): IO[String, Validation[String, Unit]] = {
    JWTVerification.validateEncodedJwt(payload.proof.jwt, proofPurpose)(didResolver: DidResolver)(claim =>
      Validation.fromEither(claim.fromJson[W3cCredentialPayload])
    )(vc =>
      vc.issuer match {
        case string: String                     => string
        case credentialIssuer: CredentialIssuer => credentialIssuer.id
      }
    )
  }

  def verifyDates(w3cPayload: W3cVerifiableCredentialPayload, leeway: TemporalAmount)(implicit
      clock: Clock
  ): Validation[String, Unit] = {
    Validation.validateWith(
      CredentialVerification.verifyDates(
        w3cPayload.payload.maybeValidFrom,
        w3cPayload.payload.maybeValidUntil,
        leeway,
        "validFrom",
        "validUntil"
      )(clock),
      CredentialVerification.verifyDates(
        Some(w3cPayload.payload.issuanceDate),
        w3cPayload.payload.maybeExpirationDate,
        leeway,
        "issuanceDate",
        "expirationDate"
      )(
        clock
      )
    )((l, _) => l)
  }

  private def verifyRevocationStatusW3c(
      w3cPayload: W3cVerifiableCredentialPayload,
  )(uriResolver: UriResolver): IO[String, Validation[String, Unit]] = {
    val credentialStatus = w3cPayload.payload.maybeCredentialStatus
      .map {
        {
          case status: CredentialStatus           => List(status)
          case statusList: List[CredentialStatus] => statusList
        }
      }
      .getOrElse(List.empty)
    for {
      results <- ZIO.collectAll(
        credentialStatus.map(status => CredentialVerification.verifyCredentialStatus(status)(uriResolver))
      )
      result = Validation.validateAll(results).flatMap(_ => Validation.unit)
    } yield result
  }

  def verify(w3cPayload: W3cVerifiableCredentialPayload, options: CredentialVerification.CredentialVerificationOptions)(
      didResolver: DidResolver,
      uriResolver: UriResolver
  )(implicit clock: Clock): IO[String, Validation[String, Unit]] = {
    for {
      signatureValidation <-
        if (options.verifySignature) then validateW3C(w3cPayload, options.maybeProofPurpose)(didResolver)
        else ZIO.succeed(Validation.unit)
      dateVerification <- ZIO.succeed(
        if (options.verifyDates) then verifyDates(w3cPayload, options.leeway) else Validation.unit
      )
      revocationVerification <- verifyRevocationStatusW3c(w3cPayload)(uriResolver)
    } yield Validation.validateWith(signatureValidation, dateVerification, revocationVerification)((a, _, _) => a)
  }
}
