package org.hyperledger.identus.pollux.sdjwt

import sdjwtwrapper.*
import zio.json.*
import zio.json.ast.Json
import zio.json.internal.Write

import scala.util.{Failure, Success, Try}

object SDJWT {

  sealed trait ClaimsValidationResult
  sealed trait Valid extends ClaimsValidationResult
  case object ValidAnyMatch extends Valid
  case class ValidClaims(claims: Json.Obj) extends Valid {
    def verifyDiscoseClaims(query: Json.Obj): SDJWT.ValidAnyMatch.type | SDJWT.ClaimsDoNotMatch.type =
      if (QueryUtils.testClaims(query, claims)) SDJWT.ValidAnyMatch else SDJWT.ClaimsDoNotMatch

    def verifyDiscoseClaims(
        query: Json.Obj,
        iss: Option[String],
        sub: Option[String],
        iat: Option[Long],
        exp: Option[Long],
    ): SDJWT.ValidAnyMatch.type | SDJWT.ClaimsDoNotMatch.type = {
      val fullQuery = Seq(
        iss.map("iss" -> Json.Str(_)),
        sub.map("sub" -> Json.Str(_)),
        iat.map("iat" -> Json.Num(_)),
        exp.map("exp" -> Json.Num(_)),
      ).flatten.foldLeft(query)((q, v) => q.add(v._1, v._2))
      verifyDiscoseClaims(fullQuery)
    }
  }
  sealed trait Invalid extends ClaimsValidationResult
  case object InvalidSignature extends Invalid { def error = "Fail due to invalid input: InvalidSignature" }
  case object InvalidToken extends Invalid { def error = "Fail due to invalid input: InvalidToken" }
  case object InvalidState extends Invalid { def error = "Fail due to invalid claim: Requested claim doesn't exist" }
  case object InvalidClaims extends Invalid { def error = "Fail to Verify the claims" }
  case object ClaimsDoNotMatch extends Invalid { def error = "Claims (are valid) but do not match the expected value" }
  case object InvalidClaimsIsNotJsonObj extends Invalid { def error = "The claims must be a valid json Obj" }
  case class InvalidError(error: String) extends Invalid

  def issueCredential(
      issueKey: IssuerPrivateKey,
      claims: String,
  ): CredentialCompact = issueCredential(issueKey, claims, None)

  def issueCredential(
      issueKey: IssuerPrivateKey,
      claimsMap: Map[String, String],
  ): CredentialCompact = {

    given encoder: JsonEncoder[String | Int] = (b: String | Int, indent: Option[Int], out: Write) => {
      b match {
        case obj: String => JsonEncoder.string.unsafeEncode(obj, indent, out)
        case obj: Int    => JsonEncoder.int.unsafeEncode(obj, indent, out)
      }
    }

    val claims = claimsMap ++
      Map("sub" -> "did:example:holder", "iss" -> "did:example:issuer", "iat" -> 1683000000, "exp" -> 1883000000)
    issueCredential(issueKey, claims.toJson, None)
  }

  def issueCredential(
      issueKey: IssuerPrivateKey,
      claims: String,
      holderKey: HolderPublicKey,
  ): CredentialCompact = issueCredential(issueKey, claims, Some(holderKey))

  private def issueCredential(
      issueKey: IssuerPrivateKey,
      claims: String,
      holderKey: Option[HolderPublicKey]
  ): CredentialCompact = {
    val issuer = new SdjwtIssuerWrapper(issueKey.value, issueKey.signAlg) // null)
    val sdjwt = issuer.issueSdJwtAllLevel(
      claims, // user_claims
      holderKey.map(_.jwt).orNull, // holder_key
      false, // add_decoy_claims
      SdjwtSerializationFormat.COMPACT // COMPACT // serialization_format
    )
    CredentialCompact.unsafeFromCompact(sdjwt)
  }

  def createPresentation(
      sdjwt: CredentialCompact,
      claimsToDisclose: String,
  ): PresentationCompact = {
    val holder = SdjwtHolderWrapper(sdjwt.compact, SdjwtSerializationFormat.COMPACT)
    val presentation = holder.createPresentation(
      claimsToDisclose,
      null, // nonce
      null, // aud
      null, // holder_key
      null, // signAlg, // sign_alg
    )
    PresentationCompact.unsafeFromCompact(presentation)
  }

  /** Create a presentation with challenge
    *
    * @param sdjwt
    * @param claimsToDisclose
    * @param nonce
    * @param aud
    * @param holderKey
    * @return
    *   A presentation
    */
  def createPresentation(
      sdjwt: CredentialCompact,
      claimsToDisclose: String,
      nonce: String,
      aud: String,
      holderKey: HolderPrivateKey
  ): PresentationCompact = {
    val holder = SdjwtHolderWrapper(sdjwt.compact, SdjwtSerializationFormat.COMPACT)
    val presentation = holder.createPresentation(
      claimsToDisclose,
      nonce, // nonce
      aud, // aud
      holderKey.value, // encodingKey("ES256"), // holder_key
      holderKey.signAlg, // null, // sign_alg
    )
    PresentationCompact.unsafeFromCompact(presentation)
  }

  def getVerifiedClaims(
      key: IssuerPublicKey,
      presentation: PresentationCompact,
  ): ClaimsValidationResult = {
    Try {
      val verifier = SdjwtVerifierWrapper(
        presentation.compact, // sd_jwt_presentation
        key.pem, // public_key
        null, // expected_aud
        null, // expected_nonce
        SdjwtSerializationFormat.COMPACT // serialization_format
      )
      verifier.getVerifiedClaims()
    } match {
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidSignature" =>
        InvalidSignature
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidToken" =>
        InvalidToken
      case Failure(ex: SdjwtException.Unspecified)
          if ex.getMessage() == "invalid state: Requested claim doesn't exist" =>
        InvalidToken
      case Failure(ex)     => InvalidError(ex.getMessage())
      case Success(claims) =>
        claims.fromJson[Json] match
          case Left(value)           => InvalidClaimsIsNotJsonObj
          case Right(json: Json.Obj) => ValidClaims(json)
          case Right(json)           => InvalidClaimsIsNotJsonObj

    }
  }

  def getVerifiedClaims(
      key: IssuerPublicKey,
      presentation: PresentationCompact,
      expectedNonce: String,
      expectedAud: String,
      // holderKey: HolderPrivateKey
  ): ClaimsValidationResult = {
    Try {
      val verifier = SdjwtVerifierWrapper(
        presentation.compact, // sd_jwt_presentation
        key.pem, // public_key
        expectedAud, // expected_aud
        expectedNonce, // expected_nonce
        SdjwtSerializationFormat.COMPACT // serialization_format
      )
      verifier.getVerifiedClaims()
    } match {
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidSignature" =>
        InvalidSignature
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidToken" =>
        InvalidToken
      case Failure(ex)     => InvalidError(ex.getMessage())
      case Success(claims) =>
        claims.fromJson[Json] match
          case Left(value)           => InvalidClaimsIsNotJsonObj
          case Right(json: Json.Obj) => ValidClaims(json)
          case Right(json)           => InvalidClaimsIsNotJsonObj
    }
  }

  @deprecated("use getVerifiedClaims instaded", "ever")
  def verifyAndComparePresentation(
      key: IssuerPublicKey,
      presentation: PresentationCompact,
      claims: String
  ): ClaimsValidationResult = {
    Try {
      val verifier = SdjwtVerifierWrapper(
        presentation.compact, // sd_jwt_presentation
        key.pem, // public_key
        null, // expected_aud
        null, // expected_nonce
        SdjwtSerializationFormat.COMPACT // serialization_format
      )
      verifier.verify(claims)
    } match {
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidSignature" =>
        InvalidSignature
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidToken" =>
        InvalidToken
      case Failure(ex)    => InvalidError(ex.getMessage())
      case Success(true)  => ValidAnyMatch
      case Success(false) => InvalidClaims
    }
  }

  /** Verify Presentation with challenge
    *
    * @param key
    * @param presentation
    * @param claims
    * @param expectedNonce
    *   the presentation challenge
    * @param expectedAud
    * @return
    *   the result of the verification
    */
  @deprecated("use getVerifiedClaims instaded", "ever")
  def verifyAndComparePresentation(
      key: IssuerPublicKey,
      presentation: PresentationCompact,
      claims: String,
      expectedNonce: String,
      expectedAud: String,
      // holderKey: HolderPrivateKey
  ): ClaimsValidationResult = {
    Try {
      val verifier = SdjwtVerifierWrapper(
        presentation.compact, // sd_jwt_presentation
        key.pem, // public_key
        expectedAud, // expected_aud
        expectedNonce, // expected_nonce
        SdjwtSerializationFormat.COMPACT // serialization_format
      )
      verifier.verify(claims)
    } match {
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidSignature" =>
        InvalidSignature
      case Failure(ex: SdjwtException.Unspecified) if ex.getMessage() == "invalid input: InvalidToken" =>
        InvalidToken
      case Failure(ex)    => InvalidError(ex.getMessage())
      case Success(true)  => ValidAnyMatch
      case Success(false) => InvalidClaims
    }
  }
}
