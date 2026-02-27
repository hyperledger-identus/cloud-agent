package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.pollux.core.model.CredentialFormat as PolluxCredentialFormat
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.encodedName
import zio.json.*

// The idea to have a hierarchy of requests is to be able to use the same endpoint for different types of requests
// This trait contains the common fields for all the requests
@jsonDiscriminator("format")
sealed trait CredentialRequest {
  // REQUIRED when the credential_identifiers parameter was not returned from the Token Response.
  // It MUST NOT be used otherwise
  // It not an optional in this implementation to help to define the serializer and schema
  val format: CredentialFormat
  // Object containing the proof of possession of the cryptographic key material the issued Credential would be bound to.
  // The proof object is REQUIRED if the proof_types_supported parameter is non-empty and present in the credential_configurations_supported parameter of the Issuer metadata for the requested Credential.
  val proof: Option[Proof]
  // REQUIRED when credential_identifiers parameter was returned from the Token Response. It MUST NOT be used otherwise.
  // It is a String that identifies a Credential that is being requested to be issued.
  val credentialIdentifier: Option[String]
  // OPTIONAL. Object containing information for encrypting the Credential Response.
  // If this request element is not present, the corresponding credential response returned is not encrypted
  val credentialResponseEncryption: Option[CredentialResponseEncryption]
}

object CredentialRequest {
  given schema: Schema[CredentialRequest] = Schema
    .oneOfUsingField[CredentialRequest, CredentialFormat](_.format, _.toString)(
      CredentialFormat.jwt_vc_json -> JwtCredentialRequest.schema,
      CredentialFormat.anoncreds -> AnoncredsCredentialRequest.schema
    )
  given encoder: JsonEncoder[CredentialRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialRequest] = DeriveJsonDecoder.gen
}

@jsonHint(CredentialFormat.jwt_vc_json.toString)
case class JwtCredentialRequest(
    format: CredentialFormat,
    proof: Option[Proof],
    @jsonField("credential_identifier")
    @encodedName("credential_identifier")
    credentialIdentifier: Option[String],
    @jsonField("credential_response_encryption")
    @encodedName("credential_response_encryption")
    credentialResponseEncryption: Option[CredentialResponseEncryption],
    // REQUIRED when the format parameter is present in the Credential Request.
    // It MUST NOT be used otherwise. It is an object containing the detailed description of the Credential type.
    @jsonField("credential_definition")
    @encodedName("credential_definition")
    credentialDefinition: Option[CredentialDefinition]
) extends CredentialRequest

object JwtCredentialRequest {
  given schema: Schema[JwtCredentialRequest] = Schema.derived
  given encoder: JsonEncoder[JwtCredentialRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[JwtCredentialRequest] = DeriveJsonDecoder.gen
}

@jsonHint(CredentialFormat.anoncreds.toString)
case class AnoncredsCredentialRequest(
    format: CredentialFormat,
    proof: Option[Proof],
    @jsonField("credential_identifier")
    @encodedName("credential_identifier")
    credentialIdentifier: Option[String],
    @jsonField("credential_response_encryption")
    @encodedName("credential_response_encryption")
    credentialResponseEncryption: Option[CredentialResponseEncryption],
    // REQUIRED when the format parameter is present in the Credential Request.
    // It MUST NOT be used otherwise. It is an object containing the detailed description of the Credential type.
    @jsonField("credential_definition")
    @encodedName("credential_definition")
    credentialDefinition: Option[CredentialDefinition],
    anoncreds: String // TODO: it's a fake field for now
) extends CredentialRequest

object AnoncredsCredentialRequest {
  given schema: Schema[AnoncredsCredentialRequest] = Schema.derived

  given encoder: JsonEncoder[AnoncredsCredentialRequest] = DeriveJsonEncoder.gen

  given decoder: JsonDecoder[AnoncredsCredentialRequest] = DeriveJsonDecoder.gen
}

case class CredentialResponseEncryption(jwk: String, alg: String, enc: String)
object CredentialResponseEncryption {
  given schema: Schema[CredentialResponseEncryption] = Schema.derived
  given encoder: JsonEncoder[CredentialResponseEncryption] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialResponseEncryption] = DeriveJsonDecoder.gen
}

type CredentialSubject = Map[String, ClaimDescriptor]

case class CredentialDefinition(
    `@context`: Option[Seq[String]],
    `type`: Seq[String],
    credentialSubject: Option[CredentialSubject]
)
object CredentialDefinition {
  given schema: Schema[CredentialDefinition] = Schema.derived
  given encoder: JsonEncoder[CredentialDefinition] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialDefinition] = DeriveJsonDecoder.gen
}
case class ClaimDescriptor(
    mandatory: Option[Boolean],
    @jsonField("value_type")
    @encodedName("value_type")
    valueType: Option[String],
    display: Seq[Localization]
)

object ClaimDescriptor {
  given schema: Schema[ClaimDescriptor] = Schema.derived
  given encoder: JsonEncoder[ClaimDescriptor] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[ClaimDescriptor] = DeriveJsonDecoder.gen
}

case class Localization(name: String, locale: String)

object Localization {
  given schema: Schema[Localization] = Schema.derived
  given encoder: JsonEncoder[Localization] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[Localization] = DeriveJsonDecoder.gen
}

@jsonDiscriminator("proof_type")
sealed trait Proof {
  val proof_type: ProofType
}

object Proof {
  given schema: Schema[Proof] = Schema
    .oneOfUsingField[Proof, ProofType](_.proof_type, _.toString)(
      ProofType.jwt -> JwtProof.schema,
      ProofType.cwt -> CwtProof.schema,
      ProofType.ldp_vp -> LdpProof.schema
    )
  given encoder: JsonEncoder[Proof] = DeriveJsonEncoder.gen[Proof]
  given decoder: JsonDecoder[Proof] = DeriveJsonDecoder.gen[Proof]
}

@jsonHint(ProofType.jwt.toString)
case class JwtProof(
    proof_type: ProofType,
    jwt: String
) extends Proof {
  def this(jwt: String) = this(proof_type = ProofType.jwt, jwt)
}

object JwtProof {
  given schema: Schema[JwtProof] = Schema.derived
  given encoder: JsonEncoder[JwtProof] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[JwtProof] = DeriveJsonDecoder.gen
}

@jsonHint(ProofType.cwt.toString)
case class CwtProof(
    proof_type: ProofType,
    cwt: String
) extends Proof

object CwtProof {
  given schema: Schema[CwtProof] = Schema.derived
  given encoder: JsonEncoder[CwtProof] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CwtProof] = DeriveJsonDecoder.gen
}

@jsonHint(ProofType.ldp_vp.toString)
case class LdpProof(
    proof_type: ProofType,
    vp: String
) extends Proof

object LdpProof {
  given schema: Schema[LdpProof] = Schema.derived
  given encoder: JsonEncoder[LdpProof] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[LdpProof] = DeriveJsonDecoder.gen
}

enum CredentialFormat {
  case jwt_vc_json
  case anoncreds
  case `vc+sd-jwt`
}

object CredentialFormat {
  given schema: Schema[CredentialFormat] = Schema.derivedEnumeration.defaultStringBased
  given encoder: JsonEncoder[CredentialFormat] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[CredentialFormat] = JsonDecoder[String].mapOrFail { s =>
    CredentialFormat.values.find(_.toString == s).toRight(s"Unknown CredentialFormat: $s")
  }

  given Conversion[PolluxCredentialFormat, CredentialFormat] = {
    case PolluxCredentialFormat.JWT       => CredentialFormat.jwt_vc_json
    case PolluxCredentialFormat.AnonCreds => CredentialFormat.anoncreds
    case PolluxCredentialFormat.SDJWT     => CredentialFormat.`vc+sd-jwt`
  }

  given Conversion[CredentialFormat, PolluxCredentialFormat] = {
    case CredentialFormat.jwt_vc_json => PolluxCredentialFormat.JWT
    case CredentialFormat.anoncreds   => PolluxCredentialFormat.AnonCreds
    case CredentialFormat.`vc+sd-jwt` => PolluxCredentialFormat.SDJWT
  }
}

enum ProofType {
  case jwt
  case cwt
  case ldp_vp
}

object ProofType {
  given schema: Schema[ProofType] = Schema.derivedEnumeration.defaultStringBased
  given encoder: JsonEncoder[ProofType] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[ProofType] = JsonDecoder[String].mapOrFail { s =>
    ProofType.values.find(_.toString == s).toRight(s"Unknown ProofType: $s")
  }
}
