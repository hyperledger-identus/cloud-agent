package org.hyperledger.identus.credentials.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}
import zio.json.internal.Write

import java.time.Instant

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

private[jwt] val UnexpectedCodeExecutionPath =
  throw RuntimeException("Unexpected code execution path")
