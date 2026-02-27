package org.hyperledger.identus.pollux.core.model.schema

import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError
import org.hyperledger.identus.pollux.core.model.error.CredentialSchemaError.*
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.`type`.{
  AnoncredSchemaType,
  CredentialJsonSchemaType,
  CredentialSchemaType
}
import org.hyperledger.identus.pollux.core.model.schema.`type`.anoncred.AnoncredSchemaSerDesV1
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.json.{JsonSchemaValidator, JsonSchemaValidatorImpl}
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.net.URI
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

type Schema = zio.json.ast.Json

/** @param guid
  *   Globally unique identifier of the CredentialSchema object It's calculated as a UUID from string that contains the
  *   following fields: author, id and version
  * @param id
  *   Locally unique identifier of the CredentialSchema. It is UUID When the version of the credential schema changes
  *   this `id` keeps the same value
  * @param name
  *   Human readable name of the CredentialSchema
  * @param version
  *   Version of the CredentialSchema
  * @param author
  *   DID of the CredentialSchema's author
  * @param authored
  *   Datetime stamp of the schema creation
  * @param tags
  *   Tags of the CredentialSchema used for convenient lookup
  * @param description
  *   Human readable description of the schema
  * @param schema
  *   Internal schema object that depends on concrete implementation For W3C JsonSchema it is a JsonSchema object For
  *   AnonCreds schema is a AnonCreds schema
  */
//TODO: refactor `id` to use UriString
case class CredentialSchema(
    guid: UUID,
    id: UUID,
    name: String,
    version: String,
    author: String,
    authored: OffsetDateTime,
    tags: Seq[String],
    description: String,
    `type`: String,
    resolutionMethod: ResourceResolutionMethod,
    schema: Schema
) {
  def longId = CredentialSchema.makeLongId(author, id, version)
}

object CredentialSchema {

  def makeLongId(author: String, id: UUID, version: String) =
    s"$author/${id.toString}?version=${version}"

  def makeGUID(author: String, id: UUID, version: String) =
    UUID.nameUUIDFromBytes(makeLongId(author, id, version).getBytes)

  def make(in: Input, resolutionMethod: ResourceResolutionMethod): UIO[CredentialSchema] = {
    for {
      id <- zio.Random.nextUUID
      cs <- make(id, in, resolutionMethod)
    } yield cs
  }
  def make(id: UUID, in: Input, resolutionMethod: ResourceResolutionMethod): UIO[CredentialSchema] = {
    for {
      ts <- zio.Clock.currentDateTime.map(
        _.atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime
      )
      guid = makeGUID(in.author, id, in.version)
    } yield CredentialSchema(
      guid = guid,
      id = id,
      name = in.name,
      version = in.version,
      author = in.author,
      authored = ts,
      tags = in.tags,
      description = in.description,
      `type` = in.`type`,
      resolutionMethod = resolutionMethod,
      schema = in.schema
    )
  }

  val defaultAgentDid = "did:prism:agent"

  case class Input(
      name: String,
      version: String,
      description: String,
      authored: Option[OffsetDateTime],
      tags: Seq[String],
      author: String = defaultAgentDid,
      `type`: String,
      schema: Schema
  )

  case class Filter(
      author: Option[String] = None,
      name: Option[String] = None,
      version: Option[String] = None,
      tags: Option[String] = None,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  )

  case class FilteredEntries(entries: Seq[CredentialSchema], count: Long, totalCount: Long)

  given JsonEncoder[CredentialSchema] = DeriveJsonEncoder.gen[CredentialSchema]
  given JsonDecoder[CredentialSchema] = DeriveJsonDecoder.gen[CredentialSchema]

  def resolveJWTSchema(
      uri: URI,
      uriResolver: UriResolver
  ): IO[CredentialSchemaParsingError | SchemaDereferencingError, Json] = {
    for {
      content <- uriResolver
        .resolve(uri.toString)
        .mapError(SchemaDereferencingError(_))
      json <- ZIO
        .fromEither(content.fromJson[Json])
        .mapError(error => CredentialSchemaParsingError(error))
    } yield json
  }

  def validSchemaValidator(
      schemaId: UriString,
      uriResolver: UriResolver
  ): IO[CredentialSchemaParsingError | SchemaDereferencingError, JsonSchemaValidator] = {
    for {
      uri <- ZIO.succeed(schemaId.toUri)
      json <- resolveJWTSchema(uri, uriResolver)
      schemaValidator <- JsonSchemaValidatorImpl
        .from(json)
        .orElse(
          ZIO
            .fromEither(json.as[CredentialSchema])
            .mapError(error => CredentialSchemaParsingError(error))
            .flatMap(cs =>
              JsonSchemaValidatorImpl.from(cs.schema).mapError(error => CredentialSchemaParsingError(error.error))
            )
        )
    } yield schemaValidator
  }

  def validateJWTCredentialSubject(
      schemaId: UriString,
      credentialSubject: String,
      uriResolver: UriResolver
  ): IO[
    InvalidURI | CredentialSchemaParsingError | CredentialSchemaValidationError | SchemaDereferencingError,
    Unit
  ] = {
    for {
      schemaValidator <- validSchemaValidator(schemaId, uriResolver)
      _ <- schemaValidator.validate(credentialSubject).mapError(CredentialSchemaValidationError.apply)
    } yield ()
  }

  def validateAnonCredsClaims(
      schemaId: String,
      claims: String,
      uriResolver: UriResolver
  ): IO[InvalidURI | CredentialSchemaParsingError | VCClaimsParsingError | VCClaimValidationError, Unit] = {
    for {
      content <- uriResolver.resolve(schemaId).orDieAsUnmanagedFailure
      validAttrNames <-
        AnoncredSchemaSerDesV1.schemaSerDes
          .deserialize(content)
          .mapError(error => CredentialSchemaParsingError(error.error))
          .map(_.attrNames)
      jsonClaims <- ZIO.fromEither(claims.fromJson[Json]).mapError(error => VCClaimsParsingError(error))
      _ <- jsonClaims match
        case Json.Obj(fields) =>
          ZIO.foreach(fields) {
            case (k, _) if !validAttrNames.contains(k) =>
              ZIO.fail(VCClaimValidationError(k, "Name undefined in schema"))
            case (k, Json.Str(v)) => ZIO.succeed(k -> v)
            case (k, _)           => ZIO.fail(VCClaimValidationError(k, "Value should be a string"))
          }
        case _ => ZIO.fail(VCClaimsParsingError("The JSON claims is not a JSON 'object'"))
    } yield ()
  }

  private val supportedCredentialSchemaTypes: Map[String, CredentialSchemaType] =
    IndexedSeq(CredentialJsonSchemaType, AnoncredSchemaType)
      .map(credentialSchemaType => (credentialSchemaType.`type`, credentialSchemaType))
      .toMap

  def resolveCredentialSchemaType(`type`: String): IO[UnsupportedCredentialSchemaType, CredentialSchemaType] = {
    ZIO
      .fromOption(supportedCredentialSchemaTypes.get(`type`))
      .mapError(_ => UnsupportedCredentialSchemaType(`type`))
  }

  def validateCredentialSchema(
      vcSchema: CredentialSchema
  ): IO[UnsupportedCredentialSchemaType | CredentialSchemaValidationError, Unit] = {
    for {
      resolvedSchemaType <- resolveCredentialSchemaType(vcSchema.`type`)
      _ <- resolvedSchemaType.validate(vcSchema.schema).mapError(CredentialSchemaValidationError.apply)
    } yield ()
  }

}
