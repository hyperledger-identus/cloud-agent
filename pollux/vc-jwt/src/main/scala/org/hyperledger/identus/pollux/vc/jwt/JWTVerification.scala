package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jose.crypto.{ECDSAVerifier, Ed25519Verifier}
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jwt.SignedJWT
import io.circe
import io.circe.generic.auto.*
import org.hyperledger.identus.castor.core.model.did.VerificationRelationship
import org.hyperledger.identus.shared.crypto.Ed25519PublicKey
import pdi.jwt.*
import zio.*
import zio.prelude.*

import java.security.interfaces.{ECPublicKey, EdECPublicKey}
import java.security.PublicKey
import scala.util.{Failure, Success, Try}

object JWTVerification {
  // JWT algo <-> publicKey type mapping reference
  // https://github.com/decentralized-identity/did-jwt/blob/8b3655097a1382934cabdf774d580e6731a636b1/src/JWT.ts#L146
  val SUPPORT_PUBLIC_KEY_TYPES: Map[String, Set[String]] = Map(
    "ES256K" -> Set("EcdsaSecp256k1VerificationKey2019", "JsonWebKey2020"),
    "EdDSA" -> Set("Ed25519VerificationKey2020", "JsonWebKey2020"),
    // Add support for other key types here
  )

  def validateAlgorithm(jwt: JWT): Validation[String, Unit] = {
    val decodedJWT =
      Validation
        .fromTry(JwtCirce.decodeRawAll(jwt.value, JwtOptions(false, false, false)))
        .mapError(_.getMessage)
    for {
      decodedJwtTask <- decodedJWT
      (header, _, _) = decodedJwtTask
      algorithm <- Validation
        .fromOptionWith("An algorithm must be specified in the header")(JwtCirce.parseHeader(header).algorithm)
      result <-
        Validation
          .fromPredicateWith("Algorithm Not Supported")(
            SUPPORT_PUBLIC_KEY_TYPES.getOrElse(algorithm.name, Set.empty)
          )(_.nonEmpty)
          .flatMap(_ => Validation.unit)

    } yield result
  }

  def validateIssuer[T](jwt: JWT)(didResolver: DidResolver)(
      decoder: String => Validation[String, T]
  )(issuerDidExtractor: T => String): IO[String, Validation[String, DIDDocument]] = {
    val decodedJWT =
      Validation
        .fromTry(JwtCirce.decodeRawAll(jwt.value, JwtOptions(false, false, false)))
        .mapError(_.getMessage)

    val claim: Validation[String, String] =
      for {
        decodedJwtTask <- decodedJWT
        (_, claim, _) = decodedJwtTask
      } yield claim

    validateIssuerFromClaim(claim)(didResolver)(decoder)(issuerDidExtractor)
  }

  def validateIssuerFromClaim[T](validatedClaim: Validation[String, String])(didResolver: DidResolver)(
      decoder: String => Validation[String, T]
  )(issuerDidExtractor: T => String): IO[String, Validation[String, DIDDocument]] = {
    val validatedIssuerDid: Validation[String, String] =
      for {
        claim <- validatedClaim
        decodedClaim <- decoder(claim)
        extractIssuerDid = issuerDidExtractor(decodedClaim)
      } yield extractIssuerDid

    val loadDidDocument =
      ValidationUtils
        .foreach(
          validatedIssuerDid
            .map(validIssuerDid => resolve(validIssuerDid)(didResolver))
        )(identity)
        .map(b => b.flatten)

    loadDidDocument
  }

  def validateIssuerFromKeyId(
      extractedDID: Validation[String, String]
  )(didResolver: DidResolver): IO[String, Validation[String, DIDDocument]] = {
    val loadDidDocument =
      ValidationUtils
        .foreach(extractedDID.map(validIssuerDid => resolve(validIssuerDid)(didResolver)))(identity)
        .map(b => b.flatten)

    loadDidDocument
  }

  def validateEncodedJwt[T](jwt: JWT, proofPurpose: Option[VerificationRelationship] = None)(
      didResolver: DidResolver
  )(decoder: String => Validation[String, T])(issuerDidExtractor: T => String): IO[String, Validation[String, Unit]] = {
    val decodedJWT = Validation
      .fromTry(JwtCirce.decodeRawAll(jwt.value, JwtOptions(false, false, false)))
      .mapError(_.getMessage)

    val extractAlgorithm: Validation[String, JwtAlgorithm] =
      for {
        decodedJwtTask <- decodedJWT
        (header, _, _) = decodedJwtTask
        algorithm <- Validation
          .fromOptionWith("An algorithm must be specified in the header")(JwtCirce.parseHeader(header).algorithm)
      } yield algorithm

    val claim: Validation[String, String] =
      for {
        decodedJwtTask <- decodedJWT
        (_, claim, _) = decodedJwtTask
      } yield claim

    val loadDidDocument = validateIssuerFromClaim(claim)(didResolver)(decoder)(issuerDidExtractor)

    loadDidDocument
      .map(validatedDidDocument => {
        for {
          results <- Validation.validateWith(validatedDidDocument, extractAlgorithm)((didDocument, algorithm) =>
            (didDocument, algorithm)
          )
          (didDocument, algorithm) = results
          verificationMethods <- extractVerificationMethods(didDocument, algorithm, proofPurpose)
          validatedJwt <- validateEncodedJwt(jwt, verificationMethods)
        } yield validatedJwt
      })
  }

  def toECDSAVerifier(publicKey: PublicKey): JWSVerifier = {
    val verifier: JWSVerifier = publicKey match {
      case key: ECPublicKey => ECDSAVerifier(key)
      case key: EdECPublicKey =>
        val octetKeyPair = Ed25519PublicKey.toPublicKeyOctetKeyPair(key)
        Ed25519Verifier(octetKeyPair)
      case key => throw Exception(s"unsupported public-key type: ${key.getClass.getCanonicalName}")
    }
    verifier.getJCAContext.setProvider(BouncyCastleProviderSingleton.getInstance)
    verifier
  }

  def validateEncodedJwt(jwt: JWT, publicKey: PublicKey): Validation[String, Unit] = {
    Try {
      val parsedJwt = SignedJWT.parse(jwt.value)
      // TODO Implement other key types
      val verifier = toECDSAVerifier(publicKey)
      parsedJwt.verify(verifier)
    } match {
      case Failure(exception) => Validation.fail(s"Jwt[$jwt] verification pre-process failed: ${exception.getMessage}")
      case Success(isValid) =>
        if isValid then Validation.unit else Validation.fail(s"Jwt[$jwt] not signed by $publicKey")
    }
  }

  def validateEncodedJwt(jwt: JWT, verificationMethods: IndexedSeq[VerificationMethod]): Validation[String, Unit] = {
    verificationMethods
      .map(verificationMethod => {
        for {
          publicKey <- extractPublicKey(verificationMethod)
          signatureValidation <- validateEncodedJwt(jwt, publicKey)
        } yield signatureValidation
      })
      .reduce((v1, v2) => v1.orElse(v2))
  }

  def resolve(issuerDid: String)(didResolver: DidResolver): IO[String, Validation[String, DIDDocument]] = {
    didResolver
      .resolve(issuerDid)
      .map(
        _ match
          case (didResolutionSucceeded: DIDResolutionSucceeded) =>
            Validation.succeed(didResolutionSucceeded.didDocument)
          case (didResolutionFailed: DIDResolutionFailed) => Validation.fail(didResolutionFailed.error.toString)
      )
  }

  private def extractVerificationMethods(
      didDocument: DIDDocument,
      jwtAlgorithm: JwtAlgorithm,
      proofPurpose: Option[VerificationRelationship]
  ): Validation[String, IndexedSeq[VerificationMethod]] = {
    val publicKeysToCheck: Vector[VerificationMethodOrRef] = proofPurpose.fold(didDocument.verificationMethod) {
      case VerificationRelationship.Authentication       => didDocument.authentication
      case VerificationRelationship.AssertionMethod      => didDocument.assertionMethod
      case VerificationRelationship.KeyAgreement         => didDocument.keyAgreement
      case VerificationRelationship.CapabilityInvocation => didDocument.capabilityInvocation
      case VerificationRelationship.CapabilityDelegation => didDocument.capabilityDelegation
    }
    // FIXME
    // To be fully compliant, key extraction MUST follow the referenced URI which
    // might not be in the same DID document. For now, this only performs lookup within
    // the same DID document which is what Prism DID currently support.

    val dereferencedKeysToCheck = dereferenceVerificationMethods(didDocument, publicKeysToCheck)

    Validation
      .fromPredicateWith("No PublicKey to validate against found")(
        dereferencedKeysToCheck.filter { verification =>
          val supportPublicKeys = SUPPORT_PUBLIC_KEY_TYPES.getOrElse(jwtAlgorithm.name, Set.empty)
          supportPublicKeys.contains(verification.`type`)
        }
      )(_.nonEmpty)
  }

  def dereferenceVerificationMethods(
      didDocument: DIDDocument,
      methods: Vector[VerificationMethodOrRef]
  ): Vector[VerificationMethod] = {
    val (referenced, embedded) = methods.partitionMap[String, VerificationMethod] {
      case uri: String            => Left(uri)
      case pk: VerificationMethod => Right(pk)
    }
    val keySet = referenced.toSet
    embedded ++ didDocument.verificationMethod.filter(pk => keySet.contains(pk.id))
  }

  // TODO Implement other key types
  def extractPublicKey(verificationMethod: VerificationMethod): Validation[String, PublicKey] = {
    val maybePublicKey =
      for {
        publicKeyJwk <- verificationMethod.publicKeyJwk
        curve <- publicKeyJwk.crv

        key <- curve match
          case "Ed25519" =>
            publicKeyJwk.x.map(Base64URL.from).map { base64 =>
              Ed25519PublicKey.toJavaEd25519PublicKey(base64.decode())
            }
          case "secp256k1" =>
            for {
              x <- publicKeyJwk.x.map(Base64URL.from)
              y <- publicKeyJwk.y.map(Base64URL.from)
            } yield new ECKey.Builder(Curve.parse(curve), x, y).build().toPublicKey

      } yield key
    Validation.fromOptionWith("Unable to parse Public Key")(maybePublicKey)
  }

  def extractJwtHeader(jwt: JWT): Validation[String, JwtHeader] = {
    import io.circe.parser._
    import io.circe.Json

    def parseHeaderUnsafe(json: Json): Either[String, JwtHeader] =
      Try(JwtCirce.parseHeader(json.noSpaces)).toEither.left.map(_.getMessage)

    def decodeJwtHeader(header: String): Validation[String, JwtHeader] = {
      val eitherResult = for {
        json <- parse(header).left.map(_.getMessage)
        jwtHeader <- parseHeaderUnsafe(json)
      } yield jwtHeader
      Validation.fromEither(eitherResult)
    }
    for {
      decodedJwt <- Validation
        .fromTry(JwtCirce.decodeRawAll(jwt.value, JwtOptions(false, false, false)))
        .mapError(_.getMessage)
      (header, _, _) = decodedJwt
      jwtHeader <- decodeJwtHeader(header)
    } yield jwtHeader
  }
}
