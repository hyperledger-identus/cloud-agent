package org.hyperledger.identus.shared.json

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.networknt.schema.*
import com.networknt.schema.SpecVersion.VersionFlag
import org.hyperledger.identus.shared.json.JsonSchemaError.{
  JsonSchemaParsingError,
  UnexpectedError,
  UnsupportedJsonSchemaSpecVersion
}
import zio.*
import zio.json.*
import zio.json.ast.Json

import scala.io.Source

sealed trait JsonSchemaError {
  def error: String
}

object JsonSchemaError {
  case class JsonSchemaParsingError(error: String) extends JsonSchemaError

  case class JsonValidationErrors(errors: Seq[String]) extends JsonSchemaError {
    def error: String = errors.mkString(";")
  }

  case class UnsupportedJsonSchemaSpecVersion(error: String) extends JsonSchemaError

  case class UnexpectedError(error: String) extends JsonSchemaError
}

trait JsonSchemaValidator {
  def validate(claims: String): IO[JsonSchemaError, Unit]

  def validate(claimsJsonNode: JsonNode): IO[JsonSchemaError, Unit] = {
    validate(claimsJsonNode.toString)
  }
}

case class JsonSchemaValidatorImpl(schemaValidator: JsonSchema) extends JsonSchemaValidator {
  override def validate(jsonString: String): IO[JsonSchemaError, Unit] = {
    import scala.jdk.CollectionConverters.*
    for {
      // Convert claims to JsonNode
      jsonClaims <- JsonSchemaUtils.toJsonNode(jsonString)

      // Validate claims JsonNode
      validationMessages <- ZIO
        .attempt(schemaValidator.validate(jsonClaims).asScala.toSeq)
        .mapError(t => JsonSchemaError.JsonValidationErrors(Seq(t.getMessage)))

      validationResult <-
        if (validationMessages.isEmpty) ZIO.unit
        else ZIO.fail(JsonSchemaError.JsonValidationErrors(validationMessages.map(_.getMessage)))
    } yield validationResult
  }

}

object JsonSchemaValidatorImpl {
  def from(schema: Json): IO[JsonSchemaError, JsonSchemaValidator] = {
    for {
      jsonSchema <- JsonSchemaUtils.from(schema, IndexedSeq(SpecVersion.VersionFlag.V202012))
    } yield JsonSchemaValidatorImpl(jsonSchema)
  }

  def draft7Meta: ZIO[Scope, JsonSchemaError, JsonSchemaValidator] =
    ZIO
      .acquireRelease {
        ZIO
          .attempt(Source.fromResource("json-schema/draft-07.json"))
          .mapError(e => UnexpectedError(e.getMessage))
      }(src => ZIO.attempt(src).orDie)
      .map(_.mkString)
      .flatMap(schema => JsonSchemaUtils.jsonSchemaAtVersion(schema, SpecVersion.VersionFlag.V7))
      .map(JsonSchemaValidatorImpl(_))
}

object JsonSchemaUtils {

  /** Create a json schema where the version is inferred from $schema field with optional supported version check */
  def jsonSchema(
      schema: String,
      supportedVersions: IndexedSeq[VersionFlag] = IndexedSeq.empty
  ): IO[JsonSchemaError, JsonSchema] = {
    for {
      jsonSchemaNode <- toJsonNode(schema)
      specVersion <- ZIO
        .attempt(SpecVersionDetector.detect(jsonSchemaNode))
        .mapError(t => UnexpectedError(t.getMessage))
      _ <-
        if (supportedVersions.nonEmpty && !supportedVersions.contains(specVersion))
          ZIO.fail(
            UnsupportedJsonSchemaSpecVersion(
              s"Unsupported JsonSchemaVersion. Current:$specVersion ExpectedOneOf:${supportedVersions.map(_.getId)}"
            )
          )
        else ZIO.unit
      jsonSchema <- jsonSchemaAtVersion(schema, specVersion)
    } yield jsonSchema
  }

  /** Create a json schema at specific version */
  def jsonSchemaAtVersion(
      schema: String,
      specVersion: VersionFlag
  ): IO[JsonSchemaError, JsonSchema] = {
    for {
      jsonSchemaNode <- toJsonNode(schema)
      mapper <- ZIO.attempt(new ObjectMapper()).mapError(t => UnexpectedError(t.getMessage))
      factory <- ZIO
        .attempt(JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(specVersion)).jsonMapper(mapper).build)
        .mapError(t => UnexpectedError(t.getMessage))
      jsonSchema <- ZIO.attempt(factory.getSchema(jsonSchemaNode)).mapError(t => UnexpectedError(t.getMessage))
    } yield jsonSchema
  }

  def from(
      schema: Json,
      supportedVersions: IndexedSeq[VersionFlag] = IndexedSeq.empty
  ): IO[JsonSchemaError, JsonSchema] = {
    jsonSchema(schema.toString(), supportedVersions)
  }

  def toJsonNode(json: Json): IO[JsonSchemaError, JsonNode] = {
    toJsonNode(json.toString())
  }

  def toJsonNode(json: String): IO[JsonSchemaError, JsonNode] = {
    for {
      mapper <- ZIO.attempt(new ObjectMapper()).mapError(t => UnexpectedError(t.getMessage))
      jsonSchemaNode <- ZIO
        .attempt(mapper.readTree(json))
        .mapError(t => JsonSchemaParsingError(t.getMessage))
    } yield jsonSchemaNode
  }
}

class SchemaSerDes[S](jsonSchemaSchemaStr: String) {

  def initialiseJsonSchema: IO[JsonSchemaError, JsonSchema] =
    JsonSchemaUtils.jsonSchema(jsonSchemaSchemaStr)

  def serializeToJsonString(instance: S)(using encoder: JsonEncoder[S]): String = {
    instance.toJson
  }

  def serialize(instance: S)(using encoder: JsonEncoder[S]): Either[String, Json] = {
    instance.toJsonAST
  }

  def deserialize(
      schema: zio.json.ast.Json
  )(using decoder: JsonDecoder[S]): IO[JsonSchemaError, S] = {
    deserialize(schema.toString())
  }

  def deserialize(
      jsonString: String
  )(using decoder: JsonDecoder[S]): IO[JsonSchemaError, S] = {
    for {
      _ <- validate(jsonString)
      schema <-
        ZIO
          .fromEither(decoder.decodeJson(jsonString))
          .mapError(JsonSchemaError.JsonSchemaParsingError.apply)
    } yield schema
  }

  def deserializeAsJson(jsonString: String): IO[JsonSchemaError, Json] = {
    for {
      _ <- validate(jsonString)
      json <-
        ZIO
          .fromEither(jsonString.fromJson[Json])
          .mapError(JsonSchemaError.JsonSchemaParsingError.apply)
    } yield json
  }

  def validate(jsonString: String): IO[JsonSchemaError, Unit] = {
    for {
      jsonSchemaSchema <- JsonSchemaUtils.jsonSchema(jsonSchemaSchemaStr)
      schemaValidator = JsonSchemaValidatorImpl(jsonSchemaSchema)
      result <- schemaValidator.validate(jsonString)
    } yield result
  }

}
