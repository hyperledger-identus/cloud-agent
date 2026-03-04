package org.hyperledger.identus.credentials.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.json.ast.{Json, JsonCursor}

import java.time.Instant

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
