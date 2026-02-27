package org.hyperledger.identus.pollux.core.model.schema

import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod.*
import zio.*
import zio.json.*

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

type Definition = zio.json.ast.Json
type CorrectnessProof = zio.json.ast.Json

/** @param guid
  *   Globally unique identifier of the CredentialDefinition object. It's calculated as a UUID from a string that
  *   contains the following fields: author, id, and version.
  * @param id
  *   Locally unique identifier of the CredentialDefinition. It is a UUID. When the version of the credential definition
  *   changes, this `id` keeps the same value.
  * @param name
  *   Human-readable name of the CredentialDefinition.
  * @param description
  *   Human-readable description of the CredentialDefinition.
  * @param version
  *   Version of the CredentialDefinition.
  * @param author
  *   DID of the CredentialDefinition's author.
  * @param authored
  *   Datetime stamp of the schema creation.
  * @param tags
  *   Tags of the CredentialDefinition, used for convenient lookup.
  * @param schemaId
  *   Schema ID that identifies the schema associated with this definition.
  * @param definition
  *   Definition object that represents the actual definition of the credential.
  * @param keyCorrectnessProof
  *   A proof that validates the correctness of the key within the context of the credential definition.
  * @param signatureType
  *   Signature type used in the CredentialDefinition.
  * @param supportRevocation
  *   Boolean flag indicating whether revocation is supported for this CredentialDefinition.
  */
case class CredentialDefinition(
    guid: UUID,
    id: UUID,
    name: String,
    description: String,
    version: String,
    author: String,
    authored: OffsetDateTime,
    tag: String,
    schemaId: String,
    definitionJsonSchemaId: String,
    definition: Definition,
    keyCorrectnessProofJsonSchemaId: String,
    keyCorrectnessProof: CorrectnessProof,
    signatureType: String,
    supportRevocation: Boolean,
    resolutionMethod: ResourceResolutionMethod
) {
  def longId = CredentialDefinition.makeLongId(author, guid, version)
}

object CredentialDefinition {

  def makeLongId(author: String, id: UUID, version: String) =
    s"$author/${id.toString}?version=${version}"

  def makeGUID(author: String, id: UUID, version: String) =
    UUID.nameUUIDFromBytes(makeLongId(author, id, version).getBytes)

  def extractGUID(longId: String): Option[UUID] = {
    longId.split("/") match {
      case Array(_, idWithVersion) =>
        idWithVersion.split("\\?") match {
          case Array(id, _) => Try(UUID.fromString(id)).toOption
          case _            => None
        }
      case _ => None
    }
  }

  def make(
      in: Input,
      definitionSchemaId: String,
      definition: Definition,
      proofSchemaId: String,
      proof: CorrectnessProof,
      resolutionMethod: ResourceResolutionMethod
  ): UIO[CredentialDefinition] = {
    for {
      id <- zio.Random.nextUUID
      cs <- make(id, in, definitionSchemaId, definition, proofSchemaId, proof, resolutionMethod)
    } yield cs
  }

  def make(
      id: UUID,
      in: Input,
      definitionSchemaId: String,
      definition: Definition,
      keyCorrectnessProofSchemaId: String,
      keyCorrectnessProof: CorrectnessProof,
      resolutionMethod: ResourceResolutionMethod
  ): UIO[CredentialDefinition] = {
    for {
      ts <- zio.Clock.currentDateTime.map(
        _.atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime
      )
      guid = makeGUID(in.author, id, in.version)
    } yield CredentialDefinition(
      guid = guid,
      id = id,
      name = in.name,
      description = in.description,
      version = in.version,
      schemaId = in.schemaId,
      author = in.author,
      authored = in.authored.map(_.atZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime).getOrElse(ts),
      tag = in.tag,
      definitionJsonSchemaId = definitionSchemaId,
      definition = definition,
      keyCorrectnessProofJsonSchemaId = keyCorrectnessProofSchemaId,
      keyCorrectnessProof = keyCorrectnessProof,
      signatureType = in.signatureType,
      supportRevocation = in.supportRevocation,
      resolutionMethod = resolutionMethod
    )
  }

  val defaultAgentDid = "did:prism:agent"

  case class Input(
      name: String,
      description: String,
      version: String,
      authored: Option[OffsetDateTime],
      tag: String,
      author: String = defaultAgentDid,
      schemaId: String,
      signatureType: String,
      supportRevocation: Boolean
  )

  case class Filter(
      author: Option[String] = None,
      name: Option[String] = None,
      version: Option[String] = None,
      tag: Option[String] = None,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  )

  case class FilteredEntries(entries: Seq[CredentialDefinition], count: Long, totalCount: Long)

  given JsonEncoder[CredentialDefinition] = DeriveJsonEncoder.gen[CredentialDefinition]

  given JsonDecoder[CredentialDefinition] = DeriveJsonDecoder.gen[CredentialDefinition]
}
