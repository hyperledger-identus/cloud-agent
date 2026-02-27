package org.hyperledger.identus.pollux.sql.model.db

import io.getquill.*
import io.getquill.context.json.PostgresJsonExtensions
import io.getquill.doobie.DoobieContext
import io.getquill.idiom.*
import org.hyperledger.identus.pollux.core.model.schema.{CorrectnessProof, Definition}
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.shared.models.WalletId

import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import java.util.UUID

case class CredentialDefinition(
    guid: UUID,
    id: UUID,
    name: String,
    version: String,
    author: String,
    authored: OffsetDateTime,
    tags: Seq[String],
    description: String,
    schemaId: String,
    definitionJsonSchemaId: String,
    definition: JsonValue[Definition],
    keyCorrectnessProofJsonSchemaId: String,
    keyCorrectnessProof: JsonValue[CorrectnessProof],
    signatureType: String,
    supportRevocation: Boolean,
    resolutionMethod: ResourceResolutionMethod,
    walletId: WalletId
) {
  lazy val uniqueConstraintKey = author + name + version

  def withTruncatedTimestamp(unit: ChronoUnit = ChronoUnit.MICROS): CredentialDefinition =
    copy(authored = authored.truncatedTo(unit))
}

object CredentialDefinition {
  def fromModel(
      m: org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition,
      walletId: WalletId
  ): CredentialDefinition =
    CredentialDefinition(
      guid = m.guid,
      id = m.id,
      name = m.name,
      version = m.version,
      author = m.author,
      authored = m.authored,
      tags = Seq(m.tag),
      description = m.description,
      definitionJsonSchemaId = m.definitionJsonSchemaId,
      definition = JsonValue(m.definition),
      keyCorrectnessProofJsonSchemaId = m.keyCorrectnessProofJsonSchemaId,
      keyCorrectnessProof = JsonValue(m.keyCorrectnessProof),
      schemaId = m.schemaId,
      signatureType = m.signatureType,
      supportRevocation = m.supportRevocation,
      resolutionMethod = m.resolutionMethod,
      walletId = walletId
    )

  def toModel(
      db: CredentialDefinition
  ): org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition = {
    org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition(
      guid = db.guid,
      id = db.id,
      name = db.name,
      version = db.version,
      author = db.author,
      authored = db.authored,
      tag = db.tags.headOption.getOrElse(""),
      description = db.description,
      definitionJsonSchemaId = db.definitionJsonSchemaId,
      definition = db.definition.value,
      keyCorrectnessProofJsonSchemaId = db.keyCorrectnessProofJsonSchemaId,
      keyCorrectnessProof = db.keyCorrectnessProof.value,
      schemaId = db.schemaId,
      signatureType = db.signatureType,
      supportRevocation = db.supportRevocation,
      resolutionMethod = db.resolutionMethod
    )
  }
}

object CredentialDefinitionSql
    extends DoobieContext.Postgres(SnakeCase)
    with PostgresJsonExtensions
    with PostgresEnumEncoders {

  def insert(credentialDefinition: CredentialDefinition) = run {
    quote(
      query[CredentialDefinition]
        .insertValue(lift(credentialDefinition))
    ).returning(cs => cs)
  }

  def findByGUID(guid: UUID, resolutionMethod: ResourceResolutionMethod) = run {
    quote(
      query[CredentialDefinition]
        .filter(_.guid == lift(guid))
        .filter(_.resolutionMethod == lift(resolutionMethod))
        .take(1)
    )
  }

  def findByID(id: UUID) = run {
    quote(query[CredentialDefinition].filter(_.id == lift(id)))
  }

  def getAllVersions(id: UUID, author: String) = run {
    quote(
      query[CredentialDefinition]
        .filter(_.id == lift(id))
        .filter(_.author == lift(author))
        .sortBy(_.version)(ord = Ord.asc)
        .map(_.version)
    )
  }

  def update(credentialDefinition: CredentialDefinition) = run {
    quote {
      query[CredentialDefinition]
        .filter(_.guid == lift(credentialDefinition.guid))
        .updateValue(lift(credentialDefinition))
        .returning(s => s)
    }
  }

  def delete(guid: UUID) = run {
    quote {
      query[CredentialDefinition]
        .filter(_.guid == lift(guid))
        .delete
        .returning(cs => cs)
    }
  }

  def deleteAll = run {
    quote {
      query[CredentialDefinition].delete
    }
  }

  def totalCount = run {
    quote {
      query[CredentialDefinition].size
    }
  }

  def lookupCount(
      idOpt: Option[UUID] = None,
      authorOpt: Option[String] = None,
      nameOpt: Option[String] = None,
      versionOpt: Option[String] = None,
      tagOpt: Option[String] = None,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ) = run {
    val q =
      idOpt.fold(quote(query[CredentialDefinition]))(id =>
        quote(query[CredentialDefinition].filter(cs => cs.id == lift(id)))
      )

    q.dynamic
      .filterOpt(authorOpt)((cs, author) => quote(cs.author.like(author)))
      .filterOpt(nameOpt)((cs, name) => quote(cs.name.like(name)))
      .filterOpt(versionOpt)((cs, version) => quote(cs.version.like(version)))
      .filter(cs =>
        tagOpt
          .fold(quote(true))(tag => quote(cs.tags.contains(lift(tag))))
      )
      .filter(_.resolutionMethod == lift(resolutionMethod))
      .size
  }

  def lookup(
      idOpt: Option[UUID] = None,
      authorOpt: Option[String] = None,
      nameOpt: Option[String] = None,
      versionOpt: Option[String] = None,
      tagOpt: Option[String] = None,
      offset: Int = 0,
      limit: Int = 1000,
      resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http
  ) = run {
    val q =
      idOpt.fold(quote(query[CredentialDefinition]))(id =>
        quote(query[CredentialDefinition].filter(cs => cs.id == lift(id)))
      )

    q.dynamic
      .filterOpt(authorOpt)((cs, author) => quote(cs.author.like(author)))
      .filterOpt(nameOpt)((cs, name) => quote(cs.name.like(name)))
      .filterOpt(versionOpt)((cs, version) => quote(cs.version.like(version)))
      .filter(cs =>
        tagOpt
          .fold(quote(true))(tag => quote(cs.tags.contains(lift(tag))))
      )
      .filter(_.resolutionMethod == lift(resolutionMethod))
      .sortBy(cs => cs.id)
      .drop(offset)
      .take(limit)
  }
}
