package org.hyperledger.identus.pollux.prex

import com.networknt.schema.{JsonSchema, SpecVersion}
import org.hyperledger.identus.shared.json.{JsonPath, JsonPathError, JsonSchemaError, JsonSchemaUtils}
import zio.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json

opaque type JsonPathValue = String

object JsonPathValue {
  given Conversion[String, JsonPathValue] = identity

  given JsonEncoder[JsonPathValue] = JsonEncoder.string
  given JsonDecoder[JsonPathValue] = JsonDecoder.string

  extension (jpv: JsonPathValue) {
    def toJsonPath: Either[JsonPathError, JsonPath] = JsonPath.compile(jpv)
    def value: String = jpv
  }
}

opaque type FieldFilter = Json

object FieldFilter {
  given Conversion[Json, FieldFilter] = identity

  given JsonEncoder[FieldFilter] = Json.encoder
  given JsonDecoder[FieldFilter] = Json.decoder

  extension (f: FieldFilter)
    def asJsonZio: Json = f

    // Json schema draft 7 must be used
    // https://identity.foundation/presentation-exchange/spec/v2.1.1/#json-schema
    def toJsonSchema: IO[JsonSchemaError, JsonSchema] =
      JsonSchemaUtils.jsonSchemaAtVersion(f.toString(), SpecVersion.VersionFlag.V7)
}

case class Field(
    id: Option[String] = None,
    path: Seq[JsonPathValue] = Seq.empty,
    name: Option[String] = None,
    purpose: Option[String] = None,
    filter: Option[FieldFilter] = None,
    optional: Option[Boolean] = None
)

object Field {
  given JsonEncoder[Field] = JsonEncoder.derived
  given JsonDecoder[Field] = JsonDecoder.derived
}

case class Jwt(alg: Seq[String])

object Jwt {
  given JsonEncoder[Jwt] = JsonEncoder.derived
  given JsonDecoder[Jwt] = JsonDecoder.derived
}

case class Ldp(proof_type: Seq[String])

object Ldp {
  given JsonEncoder[Ldp] = JsonEncoder.derived
  given JsonDecoder[Ldp] = JsonDecoder.derived
}

enum ClaimFormatValue(val value: String) {
  case jwt_vc extends ClaimFormatValue("jwt_vc")
  case jwt_vp extends ClaimFormatValue("jwt_vp")
}

object ClaimFormatValue {
  given JsonEncoder[ClaimFormatValue] = JsonEncoder[String].contramap(_.value)
  given JsonDecoder[ClaimFormatValue] = JsonDecoder[String].mapOrFail {
    case "jwt_vc" => Right(ClaimFormatValue.jwt_vc)
    case "jwt_vp" => Right(ClaimFormatValue.jwt_vp)
    case other    => Left(s"Invalid ClaimFormatValue: $other")
  }
}

case class ClaimFormat(
    jwt: Option[Jwt] = None,
    jwt_vc: Option[Jwt] = None,
    jwt_vp: Option[Jwt] = None,
    ldp: Option[Ldp] = None
)

object ClaimFormat {
  given JsonEncoder[ClaimFormat] = JsonEncoder.derived
  given JsonDecoder[ClaimFormat] = JsonDecoder.derived
}

case class Constraints(fields: Option[Seq[Field]])

object Constraints {
  given JsonEncoder[Constraints] = JsonEncoder.derived
  given JsonDecoder[Constraints] = JsonDecoder.derived
}

/** Refer to <a href="https://identity.foundation/presentation-exchange/#input-descriptor">Input Descriptors</a>
  */
case class InputDescriptor(
    id: String = java.util.UUID.randomUUID.toString(),
    name: Option[String] = None,
    purpose: Option[String] = None,
    format: Option[ClaimFormat] = None,
    constraints: Constraints
)

object InputDescriptor {
  given JsonEncoder[InputDescriptor] = JsonEncoder.derived
  given JsonDecoder[InputDescriptor] = JsonDecoder.derived
}

/** Refer to <a href="https://identity.foundation/presentation-exchange/#presentation-definition">Presentation
  * Definition</a>
  */
case class PresentationDefinition(
    id: String = java.util.UUID.randomUUID.toString(), // UUID
    input_descriptors: Seq[InputDescriptor] = Seq.empty,
    name: Option[String] = None,
    purpose: Option[String] = None,
    format: Option[ClaimFormat] = None
)

object PresentationDefinition {
  given JsonEncoder[PresentationDefinition] = JsonEncoder.derived
  given JsonDecoder[PresentationDefinition] = JsonDecoder.derived
}
