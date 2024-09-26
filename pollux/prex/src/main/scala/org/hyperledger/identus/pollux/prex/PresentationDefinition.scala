package org.hyperledger.identus.pollux.prex

import com.networknt.schema.{JsonSchema, SpecVersion}
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.Json as CirceJson
import org.hyperledger.identus.shared.json.{JsonInterop, JsonPath, JsonPathError, JsonSchemaError, JsonSchemaUtils}
import zio.*
import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json as ZioJson

opaque type JsonPathValue = String

object JsonPathValue {
  given Encoder[JsonPathValue] = Encoder.encodeString
  given Decoder[JsonPathValue] = Decoder.decodeString
  given Conversion[String, JsonPathValue] = identity

  given JsonEncoder[JsonPathValue] = JsonEncoder.string
  given JsonDecoder[JsonPathValue] = JsonDecoder.string

  extension (jpv: JsonPathValue) {
    def toJsonPath: Either[JsonPathError, JsonPath] = JsonPath.compile(jpv)
    def value: String = jpv
  }
}

opaque type FieldFilter = ZioJson

object FieldFilter {
  given Encoder[FieldFilter] = Encoder.encodeJson.contramap(JsonInterop.toCirceJsonAst)
  given Decoder[FieldFilter] = Decoder.decodeJson.map(JsonInterop.toZioJsonAst)
  given Conversion[ZioJson, FieldFilter] = identity

  given JsonEncoder[FieldFilter] = ZioJson.encoder
  given JsonDecoder[FieldFilter] = ZioJson.decoder

  extension (f: FieldFilter)
    def asJsonZio: ZioJson = f
    def asJsonCirce: CirceJson = JsonInterop.toCirceJsonAst(f)

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
  given Encoder[Field] = deriveEncoder[Field]
  given Decoder[Field] = deriveDecoder[Field]

  given JsonEncoder[Field] = JsonEncoder.derived
  given JsonDecoder[Field] = JsonDecoder.derived
}

case class Jwt(alg: Seq[String])

object Jwt {
  given Encoder[Jwt] = deriveEncoder[Jwt]
  given Decoder[Jwt] = deriveDecoder[Jwt]

  given JsonEncoder[Jwt] = JsonEncoder.derived
  given JsonDecoder[Jwt] = JsonDecoder.derived
}

case class Ldp(proof_type: Seq[String])

object Ldp {
  given Encoder[Ldp] = deriveEncoder[Ldp]
  given Decoder[Ldp] = deriveDecoder[Ldp]

  given JsonEncoder[Ldp] = JsonEncoder.derived
  given JsonDecoder[Ldp] = JsonDecoder.derived
}

enum ClaimFormatValue {
  case jwt_vc, jwt_vp
}

object ClaimFormatValue {
  given Encoder[ClaimFormatValue] = Encoder.encodeString.contramap(_.toString())
  given Decoder[ClaimFormatValue] = Decoder.decodeString.emap { s =>
    ClaimFormatValue.values.find(_.toString() == s).toRight(s"Invalid ClaimFormatValue: $s")
  }

  given JsonEncoder[ClaimFormatValue] = JsonEncoder.string.contramap(_.toString())
  given JsonDecoder[ClaimFormatValue] = JsonDecoder.string.mapOrFail { s =>
    ClaimFormatValue.values.find(_.toString() == s).toRight(s"Invalid ClaimFormatValue: $s")
  }
}

case class ClaimFormat(
    jwt: Option[Jwt] = None,
    jwt_vc: Option[Jwt] = None,
    jwt_vp: Option[Jwt] = None,
    ldp: Option[Ldp] = None
)

object ClaimFormat {
  given Encoder[ClaimFormat] = deriveEncoder[ClaimFormat]
  given Decoder[ClaimFormat] = deriveDecoder[ClaimFormat]

  given JsonEncoder[ClaimFormat] = JsonEncoder.derived
  given JsonDecoder[ClaimFormat] = JsonDecoder.derived
}

case class Constraints(fields: Option[Seq[Field]])

object Constraints {
  given Encoder[Constraints] = deriveEncoder[Constraints]
  given Decoder[Constraints] = deriveDecoder[Constraints]

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
  given Encoder[InputDescriptor] = deriveEncoder[InputDescriptor]
  given Decoder[InputDescriptor] = deriveDecoder[InputDescriptor]

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
  given Encoder[PresentationDefinition] = deriveEncoder[PresentationDefinition]
  given Decoder[PresentationDefinition] = deriveDecoder[PresentationDefinition]

  given JsonEncoder[PresentationDefinition] = JsonEncoder.derived
  given JsonDecoder[PresentationDefinition] = JsonDecoder.derived
}
