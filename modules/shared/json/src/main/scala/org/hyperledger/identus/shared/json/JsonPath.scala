package org.hyperledger.identus.shared.json

import com.jayway.jsonpath.{InvalidPathException, JsonPath as JaywayJsonPath, PathNotFoundException}
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import zio.*
import zio.json.*
import zio.json.ast.Json

import scala.util.Try

sealed trait JsonPathError extends Failure {
  override def namespace: String = "JsonPathError"
}

object JsonPathError {
  final case class InvalidPathInput(msg: String) extends JsonPathError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"The JsonPath input is not valid: $msg"
  }

  final case class PathNotFound(path: String) extends JsonPathError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"The json path '$path' cannot be found in a json"
  }

  final case class UnexpectedCompilePathError(path: String, e: Throwable) extends JsonPathError {
    override def statusCode: StatusCode = StatusCode.InternalServerError
    override def userFacingMessage: String = s"An unhandled error occurred while compiling the JsonPath for $path"
  }

  final case class UnexpectedReadPathError(path: String, e: Throwable) extends JsonPathError {
    override def statusCode: StatusCode = StatusCode.InternalServerError
    override def userFacingMessage: String = s"An unhandled error occurred while reading the JsonPath for $path"
  }
}

opaque type JsonPath = JaywayJsonPath

object JsonPath {
  def compileUnsafe(path: String): JsonPath = JaywayJsonPath.compile(path)

  def compile(path: String): Either[JsonPathError, JsonPath] =
    Try(compileUnsafe(path)).toEither.left
      .map {
        case e: IllegalArgumentException => JsonPathError.InvalidPathInput(e.getMessage())
        case e: InvalidPathException     => JsonPathError.InvalidPathInput(e.getMessage())
        case e                           => JsonPathError.UnexpectedCompilePathError(path, e)
      }

  extension (jsonPath: JsonPath) {
    def read(json: Json): Either[JsonPathError, Json] = {
      val jsonProvider = JacksonJsonProvider()
      val document = JaywayJsonPath.parse(json.toString())
      for {
        queriedObj <- Try(document.read[java.lang.Object](jsonPath)).toEither.left.map {
          case e: PathNotFoundException => JsonPathError.PathNotFound(jsonPath.getPath())
          case e                        => JsonPathError.UnexpectedReadPathError(jsonPath.getPath(), e)
        }
        queriedJsonStr = jsonProvider.toJson(queriedObj)
        queriedJson <- queriedJsonStr
          .fromJson[Json]
          .left
          .map(e => JsonPathError.UnexpectedReadPathError(jsonPath.getPath(), Exception(e)))
      } yield queriedJson
    }
  }
}
