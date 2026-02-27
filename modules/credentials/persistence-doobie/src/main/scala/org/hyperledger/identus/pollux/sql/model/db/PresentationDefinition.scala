package org.hyperledger.identus.pollux.sql.model.db

import io.getquill.*
import io.getquill.context.json.PostgresJsonExtensions
import io.getquill.doobie.DoobieContext
import io.getquill.idiom.*
import org.hyperledger.identus.pollux.prex
import org.hyperledger.identus.shared.models.WalletId

import java.time.Instant
import java.util.UUID

case class PresentationDefinition(
    id: UUID,
    input_descriptors: JsonValue[Seq[prex.InputDescriptor]],
    name: Option[String],
    purpose: Option[String],
    format: Option[JsonValue[prex.ClaimFormat]],
    createdAt: Instant,
    walletId: WalletId
)

object PresentationDefinition {
  def fromModel(pd: prex.PresentationDefinition, walletId: WalletId, createdAt: Instant): PresentationDefinition = {
    PresentationDefinition(
      id = UUID.fromString(pd.id),
      input_descriptors = JsonValue(pd.input_descriptors),
      name = pd.name,
      purpose = pd.purpose,
      format = pd.format.map(JsonValue(_)),
      createdAt = createdAt,
      walletId = walletId
    )
  }

  extension (pd: PresentationDefinition) {
    def toModel: prex.PresentationDefinition = {
      prex.PresentationDefinition(
        id = pd.id.toString(),
        input_descriptors = pd.input_descriptors.value,
        name = pd.name,
        purpose = pd.purpose,
        format = pd.format.map(_.value)
      )
    }
  }
}

object PresentationDefinitionSql extends DoobieContext.Postgres(SnakeCase) with PostgresJsonExtensions {
  def insert(pd: PresentationDefinition) = run {
    quote {
      query[PresentationDefinition].insertValue(lift(pd))
    }
  }

  def findById(id: UUID) = run {
    quote {
      query[PresentationDefinition].filter(_.id == lift(id))
    }
  }

  def lookupCount() = run { quote(query[PresentationDefinition].size) }

  def lookup(offset: Int, limit: Int) = run {
    quote {
      query[PresentationDefinition].sortBy(_.createdAt).drop(lift(offset)).take(lift(limit))
    }
  }
}
