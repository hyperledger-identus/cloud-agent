package org.hyperledger.identus.pollux.prex

import org.hyperledger.identus.pollux.prex.PresentationDefinitionError.{
  DuplicatedDescriptorId,
  InvalidFilterJsonPath,
  InvalidFilterJsonSchema,
  JsonSchemaOptionNotSupported
}
import org.hyperledger.identus.shared.json.{
  JsonPathError,
  JsonSchemaError,
  JsonSchemaValidator,
  JsonSchemaValidatorImpl
}
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import zio.*

import scala.jdk.CollectionConverters.*

sealed trait PresentationDefinitionError extends Failure {
  override def namespace: String = "PresentationDefinitionError"
}

object PresentationDefinitionError {
  final case class DuplicatedDescriptorId(ids: Seq[String]) extends PresentationDefinitionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"PresentationDefinition input_descriptors contains duplicated id(s): ${ids.mkString(", ")}"
  }

  final case class InvalidFilterJsonPath(path: String, error: JsonPathError) extends PresentationDefinitionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"PresentationDefinition input_descriptors path '$path' is not a valid JsonPath"
  }

  final case class InvalidFilterJsonSchema(json: String, error: JsonSchemaError) extends PresentationDefinitionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"PresentationDefinition input_descriptors filter '$json' is not a valid JsonSchema Draft 7"
  }

  final case class JsonSchemaOptionNotSupported(invalidKeys: Set[String], allowedKeys: Set[String])
      extends PresentationDefinitionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = {
      val invalidKeysStr = invalidKeys.mkString(", ")
      val allowedKeysStr = allowedKeys.mkString(", ")
      s"PresentationDefinition input_descriptors filter json schema contains unsupported keys: $invalidKeysStr. Supported keys are: $allowedKeysStr"
    }
  }
}

trait PresentationDefinitionValidator {
  def validate(pd: PresentationDefinition): IO[PresentationDefinitionError, Unit]
}

object PresentationDefinitionValidatorImpl {
  def layer: ULayer[PresentationDefinitionValidator] =
    ZLayer.scoped {
      JsonSchemaValidatorImpl.draft7Meta
        .map(PresentationDefinitionValidatorImpl(_))
        .orDieWith(e => Exception(s"Failed to load JSON schema draft-7 meta schema: $e"))
    }
}

class PresentationDefinitionValidatorImpl(filterSchemaValidator: JsonSchemaValidator)
    extends PresentationDefinitionValidator {
  override def validate(pd: PresentationDefinition): IO[PresentationDefinitionError, Unit] = {
    val fields = pd.input_descriptors
      .flatMap(_.constraints.fields)
      .flatten

    val paths = fields.flatMap(_.path)
    val filters = fields.flatMap(_.filter)

    for {
      _ <- validateUniqueDescriptorIds(pd.input_descriptors)
      _ <- validateJsonPaths(paths)
      _ <- validateFilters(filters)
      _ <- validateAllowedFilterSchemaKeys(filters)
    } yield ()
  }

  private def validateUniqueDescriptorIds(descriptors: Seq[InputDescriptor]): IO[PresentationDefinitionError, Unit] = {
    val ids = descriptors.map(_.id)
    if ids.distinct.size == ids.size
    then ZIO.unit
    else ZIO.fail(DuplicatedDescriptorId(ids))
  }

  private def validateJsonPaths(paths: Seq[JsonPathValue]): IO[PresentationDefinitionError, Unit] = {
    ZIO
      .foreach(paths) { path =>
        ZIO.fromEither(path.toJsonPath).mapError(InvalidFilterJsonPath(path.value, _))
      }
      .unit
  }

  // while we use full-blown json-schema library, we limit the schema optiton
  // to make sure verfier don't go crazy on schema causing problem with holder interoperability
  // see SDK supported keys https://github.com/hyperledger/identus-edge-agent-sdk-ts/blob/da27890ad4ff3d32576bda8bc99a1185e7239a4c/src/domain/models/VerifiableCredential.ts#L120
  private def validateAllowedFilterSchemaKeys(filters: Seq[FieldFilter]): IO[PresentationDefinitionError, Unit] = {
    val allowedSchemaKeys = Set("type", "pattern", "enum", "const", "value")
    ZIO
      .foreach(filters) { filter =>
        val schemaKeys = filter.asJsonZio.asObject.fold(Seq.empty)(_.keys.toSeq)
        val invalidKeys = schemaKeys.filterNot(allowedSchemaKeys.contains)

        if invalidKeys.isEmpty
        then ZIO.unit
        else ZIO.fail(JsonSchemaOptionNotSupported(invalidKeys.toSet, allowedSchemaKeys))
      }
      .unit
  }

  private def validateFilters(filters: Seq[FieldFilter]): IO[PresentationDefinitionError, Unit] =
    ZIO
      .foreach(filters) { filter =>
        val json = filter.asJsonZio.toString()
        filterSchemaValidator
          .validate(json)
          .mapError(InvalidFilterJsonSchema(json, _))
      }
      .unit

}
