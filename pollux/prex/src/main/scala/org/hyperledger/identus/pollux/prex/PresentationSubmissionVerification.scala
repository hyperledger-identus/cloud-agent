package org.hyperledger.identus.pollux.prex

import io.circe.*
import io.circe.syntax.*
import org.hyperledger.identus.pollux.prex.PresentationSubmissionError.{
  ClaimDecodeFailure,
  ClaimFormatVerificationFailure,
  ClaimNotSatisfyInputConstraint,
  InvalidDataTypeForClaimFormat,
  InvalidJsonPath,
  InvalidNestedPathDescriptorId,
  InvalidSubmissionId,
  JsonPathNotFound,
  SubmissionNotSatisfyInputDescriptors
}
import org.hyperledger.identus.pollux.vc.jwt.{JWT, JwtCredential, JwtPresentation}
import org.hyperledger.identus.pollux.vc.jwt.CredentialPayload.Implicits.*
import org.hyperledger.identus.pollux.vc.jwt.PresentationPayload.Implicits.*
import org.hyperledger.identus.shared.json.{JsonInterop, JsonPath, JsonPathError, JsonSchemaValidatorImpl}
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import zio.*
import zio.json.ast.Json as ZioJson

sealed trait PresentationSubmissionError extends Failure {
  override def namespace: String = "PresentationSubmissionError"
}

object PresentationSubmissionError {
  case class InvalidSubmissionId(expected: String, actual: String) extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"Expected presentation_submission id to be $expected, got $actual"
  }

  case class InvalidNestedPathDescriptorId(expected: String, actual: String) extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"Descriptor id for all nested_path level must be the same. Expected id $expected, got $actual"
  }

  case class SubmissionNotSatisfyInputDescriptors(required: Seq[String], provided: Seq[String])
      extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"Submission does not satisfy all input descriptors. Required: ${required
        .mkString("[", ", ", "]")}, Provided: ${provided.mkString("[", ", ", "]")}"
  }

  case class InvalidDataTypeForClaimFormat(format: ClaimFormatValue, path: JsonPathValue, expectedType: String)
      extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"Expect json to be type $expectedType for claim format $format on path ${path.value}"
  }

  case class InvalidJsonPath(path: JsonPathValue, error: JsonPathError) extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"Invalid json path ${path.value} in the presentation_submission"
  }

  case class JsonPathNotFound(path: JsonPathValue) extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String = s"Json data at path ${path.value} not found in the presentation_submission"
  }

  case class ClaimDecodeFailure(format: ClaimFormatValue, path: JsonPathValue, error: String)
      extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"Unable to decode claim according to format $format at path ${path.value}: $error"
  }

  case class ClaimFormatVerificationFailure(format: ClaimFormatValue, path: JsonPathValue, error: String)
      extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"Claim format $format at path ${path.value} failed verification with errors: $error"
  }

  case class ClaimNotSatisfyInputConstraint(id: String) extends PresentationSubmissionError {
    override def statusCode: StatusCode = StatusCode.BadRequest
    override def userFacingMessage: String =
      s"Claim in presentation_submission with id $id does not satisfy input constraints"
  }
}

case class ClaimFormatVerification(
    jwtVp: JWT => IO[String, Unit],
    jwtVc: JWT => IO[String, Unit],
)

// Known issues
// 1. does not respect jwt format alg in presentation_definition
object PresentationSubmissionVerification {

  def verify(
      pd: PresentationDefinition,
      ps: PresentationSubmission,
      rootTraversalObject: ZioJson,
  )(formatVerification: ClaimFormatVerification): IO[PresentationSubmissionError, Unit] = {
    for {
      _ <- verifySubmissionId(pd, ps)
      _ <- verifySubmissionRequirement(pd, ps)
      entries <- ZIO
        .foreach(ps.descriptor_map) { descriptor =>
          extractSubmissionEntry(rootTraversalObject, descriptor)(formatVerification).map(descriptor.id -> _)
        }
      _ <- verifyInputConstraints(pd, entries)
    } yield ()
  }

  private def verifySubmissionId(
      pd: PresentationDefinition,
      ps: PresentationSubmission
  ): IO[PresentationSubmissionError, Unit] = {
    if pd.id == ps.definition_id
    then ZIO.unit
    else ZIO.fail(InvalidSubmissionId(pd.id, ps.id))
  }

  // This is not yet fully supported as described in https://identity.foundation/presentation-exchange/spec/v2.1.1/#submission-requirement-feature
  // It is now a simple check that submission descriptor_map satisfies all input_descriptors
  private def verifySubmissionRequirement(
      pd: PresentationDefinition,
      ps: PresentationSubmission
  ): IO[PresentationSubmissionError, Unit] = {
    val pdIds = pd.input_descriptors.map(_.id)
    val psIds = ps.descriptor_map.map(_.id)
    if pdIds.toSet == psIds.toSet
    then ZIO.unit
    else ZIO.fail(SubmissionNotSatisfyInputDescriptors(pdIds.toSeq, psIds.toSeq))
  }

  private def verifyInputConstraints(
      pd: PresentationDefinition,
      entries: Seq[(String, ZioJson)]
  ): IO[PresentationSubmissionError, Unit] = {
    val descriptorLookup = pd.input_descriptors.map(d => d.id -> d).toMap
    val descriptorWithEntry = entries.flatMap { case (id, entry) => descriptorLookup.get(id).map(_ -> entry) }
    ZIO
      .foreach(descriptorWithEntry) { case (descriptor, entry) =>
        verifyInputConstraint(descriptor, entry)
      }
      .unit
  }

  private def verifyInputConstraint(
      descriptor: InputDescriptor,
      entry: ZioJson
  ): IO[PresentationSubmissionError, Unit] = {
    val mandatoryFields = descriptor.constraints.fields
      .getOrElse(Nil)
      .filterNot(_.optional.getOrElse(false)) // optional field doesn't have to pass contraints

    // all fields need to be valid
    ZIO
      .foreach(mandatoryFields) { field =>
        // only one of the paths need to be valid
        ZIO
          .validateFirst(field.path) { p =>
            for {
              jsonPath <- ZIO.fromEither(p.toJsonPath)
              jsonAtPath <- ZIO.fromEither(jsonPath.read(entry))
              maybeFilter <- ZIO.foreach(field.filter)(_.toJsonSchema)
              _ <- ZIO.foreach(maybeFilter)(JsonSchemaValidatorImpl(_).validate(jsonAtPath.toString()))
            } yield ()
          }
          .mapError(_ => ClaimNotSatisfyInputConstraint(descriptor.id))
      }
      .unit
  }

  private def extractSubmissionEntry(
      traversalObject: ZioJson,
      descriptor: InputDescriptorMapping
  )(formatVerification: ClaimFormatVerification): IO[PresentationSubmissionError, ZioJson] = {
    for {
      path <- ZIO
        .fromEither(descriptor.path.toJsonPath)
        .mapError(InvalidJsonPath(descriptor.path, _))
      jsonAtPath <- ZIO
        .fromEither(path.read(traversalObject))
        .mapError(_ => JsonPathNotFound(descriptor.path))
      currentNode <- descriptor.format match {
        case ClaimFormatValue.jwt_vc => verifyJwtVc(jsonAtPath, descriptor.path)(formatVerification.jwtVc)
        case ClaimFormatValue.jwt_vp => verifyJwtVp(jsonAtPath, descriptor.path)(formatVerification.jwtVp)
      }
      leafNode <- descriptor.path_nested.fold(ZIO.succeed(currentNode)) { nestedDescriptor =>
        if descriptor.id != nestedDescriptor.id
        then ZIO.fail(InvalidNestedPathDescriptorId(descriptor.id, nestedDescriptor.id))
        else extractSubmissionEntry(currentNode, nestedDescriptor)(formatVerification)
      }
    } yield leafNode
  }

  private def verifyJwtVc(
      json: ZioJson,
      path: JsonPathValue
  )(formatVerification: JWT => IO[String, Unit]): IO[PresentationSubmissionError, ZioJson] = {
    val format = ClaimFormatValue.jwt_vc
    for {
      jwt <- ZIO
        .fromOption(json.asString)
        .map(JWT(_))
        .mapError(_ => InvalidDataTypeForClaimFormat(format, path, "string"))
      payload <- JwtCredential
        .decodeJwt(jwt)
        .mapError(e => ClaimDecodeFailure(format, path, e))
      _ <- formatVerification(jwt)
        .mapError(errors => ClaimFormatVerificationFailure(format, path, errors.mkString))
    } yield JsonInterop.toZioJsonAst(payload.asJson)
  }

  private def verifyJwtVp(
      json: ZioJson,
      path: JsonPathValue
  )(formatVerification: JWT => IO[String, Unit]): IO[PresentationSubmissionError, ZioJson] = {
    val format = ClaimFormatValue.jwt_vp
    for {
      jwt <- ZIO
        .fromOption(json.asString)
        .map(JWT(_))
        .mapError(_ => InvalidDataTypeForClaimFormat(format, path, "string"))
      payload <- ZIO
        .fromTry(JwtPresentation.decodeJwt(jwt))
        .mapError(e => ClaimDecodeFailure(format, path, e.getMessage()))
      _ <- formatVerification(jwt)
        .mapError(errors => ClaimFormatVerificationFailure(format, path, errors.mkString))
    } yield JsonInterop.toZioJsonAst(payload.asJson)
  }
}
