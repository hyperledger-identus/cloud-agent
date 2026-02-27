package org.hyperledger.identus.pollux.vc.jwt

import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json
import zio.json.internal.Write

import java.time.Instant
import scala.reflect.ClassTag

object JsonEncoders {
  given JsonEncoder[Instant] = JsonEncoder.long.contramap(_.getEpochSecond)
  given JsonDecoder[Instant] = JsonDecoder.long.map(Instant.ofEpochSecond)

  def aOrBEncoder[A: JsonEncoder: ClassTag, B: JsonEncoder: ClassTag]: JsonEncoder[A | B] =
    (a: A | B, indent: Option[Int], out: Write) =>
      a match
        case a: A => JsonEncoder[A].unsafeEncode(a, indent, out)
        case b: B => JsonEncoder[B].unsafeEncode(b, indent, out)

  def aOrBDecoder[A: JsonDecoder: ClassTag, B: JsonDecoder: ClassTag]: JsonDecoder[A | B] =
    JsonDecoder[Json].mapOrFail { json =>
      json.as[A].orElse(json.as[B])
    }

  given stringOrStringSetEncoder: JsonEncoder[String | Set[String]] =
    aOrBEncoder[String, Set[String]]
  given stringOrStringSetDecoder: JsonDecoder[String | Set[String]] =
    aOrBDecoder[String, Set[String]]

  given stringOrStringIndexedSeqEncoder: JsonEncoder[String | IndexedSeq[String]] =
    aOrBEncoder[String, IndexedSeq[String]]
  given stringOrStringIndexedSeqDecoder: JsonDecoder[String | IndexedSeq[String]] =
    aOrBDecoder[String, IndexedSeq[String]]

  given credStatusOrCredStatusListEncoder: JsonEncoder[CredentialStatus | List[CredentialStatus]] =
    aOrBEncoder[CredentialStatus, List[CredentialStatus]]
  given credStatusOrCredStatusListDecoder: JsonDecoder[CredentialStatus | List[CredentialStatus]] =
    aOrBDecoder[CredentialStatus, List[CredentialStatus]]

  given stringOrCredIssuerEncoder: JsonEncoder[String | CredentialIssuer] =
    aOrBEncoder[String, CredentialIssuer]
  given stringOrCredIssuerDecoder: JsonDecoder[String | CredentialIssuer] =
    aOrBDecoder[String, CredentialIssuer]

  given credSchemaOrCredSchemaListEncoder: JsonEncoder[CredentialSchema | List[CredentialSchema]] =
    aOrBEncoder[CredentialSchema, List[CredentialSchema]]
  given credSchemaOrCredSchemaListDecoder: JsonDecoder[CredentialSchema | List[CredentialSchema]] =
    aOrBDecoder[CredentialSchema, List[CredentialSchema]]

}
