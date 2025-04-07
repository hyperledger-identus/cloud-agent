package org.hyperledger.identus.pollux.vc.jwt.revocation

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.pollux.vc.jwt.revocation.VCStatusList2021Error.{DecodingError, EncodingError}
import zio.*

import java.time.Instant

class VCStatusList2021 private (val vcPayload: W3cCredentialPayload, jwtIssuer: Issuer) {

  def encoded: UIO[JWT] = ZIO.succeed(W3CCredential.toEncodedJwt(vcPayload, jwtIssuer))

  def toJsonWithEmbeddedProof: Task[Json] = W3CCredential.toJsonWithEmbeddedProof(vcPayload, jwtIssuer)

  def updateBitString(bitString: BitString): IO[VCStatusList2021Error, VCStatusList2021] = {
    import CredentialPayload.Implicits.*

    val res = for {
      vcId <- ZIO.fromOption(vcPayload.maybeId).mapError(_ => DecodingError("VC id not found"))
      purpose <- ZIO
        .fromEither(vcPayload.credentialSubject.hcursor.downField("statusPurpose").as[StatusPurpose])
        .mapError(x => DecodingError(x.message))
    } yield VCStatusList2021.build(vcId, jwtIssuer, bitString, purpose)

    res.flatten
  }

  def getBitString: IO[DecodingError, BitString] = {
    for {
      encodedBitString <- ZIO
        .fromOption(
          vcPayload.credentialSubject.hcursor.downField("encodedList").as[String].toOption
        )
        .mapError(_ => DecodingError("'encodedList' attribute not found in credential subject"))
      bitString <- BitString.valueOf(encodedBitString).mapError(e => DecodingError(e.message))
    } yield bitString
  }
}

object VCStatusList2021 {

  def build(
      vcId: String,
      jwtIssuer: Issuer,
      revocationData: BitString,
      purpose: StatusPurpose = StatusPurpose.Revocation
  ): IO[EncodingError, VCStatusList2021] = {
    for {
      encodedBitString <- revocationData.encoded.mapError(e => EncodingError(e.message))
    } yield {
      val claims = JsonObject()
        .add("type", "StatusList2021".asJson)
        .add("statusPurpose", purpose.toString.asJson)
        .add("encodedList", encodedBitString.asJson)
      val w3Credential = W3cCredentialPayload(
        `@context` = Set(
          "https://www.w3.org/2018/credentials/v1",
          "https://w3c.github.io/vc-bitstring-status-list/contexts/2021/v1.jsonld"
        ),
        maybeId = Some(vcId),
        `type` = Set("VerifiableCredential", "StatusList2021Credential"),
        issuer = jwtIssuer.did.toString,
        issuanceDate = Instant.now,
        maybeExpirationDate = None,
        maybeCredentialSchema = None,
        credentialSubject = claims.asJson,
        maybeCredentialStatus = None,
        maybeRefreshService = None,
        maybeEvidence = None,
        maybeTermsOfUse = None,
        maybeValidFrom = None,
        maybeValidUntil = None
      )
      VCStatusList2021(w3Credential, jwtIssuer)
    }
  }

  def decodeFromJson(json: Json, issuer: Issuer): IO[DecodingError, VCStatusList2021] = {
    import CredentialPayload.Implicits.*
    for {
      w3cCredentialPayload <- ZIO
        .fromEither(io.circe.parser.decode[W3cCredentialPayload](json.noSpaces))
        .mapError(t => DecodingError(t.getMessage))
    } yield VCStatusList2021(w3cCredentialPayload, issuer)
  }

  def decode(encodedJwtVC: JWT, issuer: Issuer): IO[DecodingError, VCStatusList2021] = {
    for {
      jwtCredentialPayload <- ZIO
        .fromTry(JwtCredential.decodeJwt(encodedJwtVC, issuer.publicKey))
        .mapError(t => DecodingError(t.getMessage))
    } yield VCStatusList2021(jwtCredentialPayload.toW3CCredentialPayload, issuer)
  }

}

sealed trait VCStatusList2021Error

object VCStatusList2021Error {
  final case class EncodingError(msg: String) extends VCStatusList2021Error
  final case class DecodingError(msg: String) extends VCStatusList2021Error
}
