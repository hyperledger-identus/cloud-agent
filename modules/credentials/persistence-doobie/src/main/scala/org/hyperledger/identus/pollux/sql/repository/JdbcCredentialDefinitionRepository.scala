package org.hyperledger.identus.pollux.sql.repository

import doobie.*
import doobie.implicits.*
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.{CredentialDefinitionRepository, Repository}
import org.hyperledger.identus.pollux.core.repository.Repository.*
import org.hyperledger.identus.pollux.sql.model.db.{
  CredentialDefinition as CredentialDefinitionRow,
  CredentialDefinitionSql
}
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.interop.catz.*

import java.util.UUID

case class JdbcCredentialDefinitionRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task])
    extends CredentialDefinitionRepository {
  import CredentialDefinitionSql.*

  override def create(cd: CredentialDefinition): URIO[WalletAccessContext, CredentialDefinition] = {
    ZIO.serviceWithZIO[WalletAccessContext](ctx =>
      CredentialDefinitionSql
        .insert(CredentialDefinitionRow.fromModel(cd, ctx.walletId))
        .transactWallet(xa)
        .orDie
        .map(CredentialDefinitionRow.toModel)
    )
  }

  override def findByGuid(guid: UUID, resolutionMethod: ResourceResolutionMethod): UIO[Option[CredentialDefinition]] = {
    CredentialDefinitionSql
      .findByGUID(guid, resolutionMethod)
      .transact(xb)
      .orDie
      .map(
        _.headOption
          .map(CredentialDefinitionRow.toModel)
      )
  }

  override def update(cd: CredentialDefinition): URIO[WalletAccessContext, CredentialDefinition] = {
    ZIO.serviceWithZIO[WalletAccessContext](ctx =>
      CredentialDefinitionSql
        .update(CredentialDefinitionRow.fromModel(cd, ctx.walletId))
        .transactWallet(xa)
        .orDie
        .map(CredentialDefinitionRow.toModel)
    )
  }

  def getAllVersions(id: UUID, author: String): URIO[WalletAccessContext, Seq[String]] = {
    CredentialDefinitionSql
      .getAllVersions(id, author)
      .transactWallet(xa)
      .orDie
  }

  override def delete(guid: UUID): URIO[WalletAccessContext, CredentialDefinition] = {
    CredentialDefinitionSql
      .delete(guid)
      .transactWallet(xa)
      .orDie
      .map(CredentialDefinitionRow.toModel)
  }

  def deleteAll(): URIO[WalletAccessContext, Unit] = {
    CredentialDefinitionSql.deleteAll
      .transactWallet(xa)
      .orDie
      .flatMap(count => ZIO.unit)
  }

  override def search(
      query: SearchQuery[CredentialDefinition.Filter]
  ): URIO[WalletAccessContext, SearchResult[CredentialDefinition]] = {
    for {
      filteredRows <- CredentialDefinitionSql
        .lookup(
          authorOpt = query.filter.author,
          nameOpt = query.filter.name,
          versionOpt = query.filter.version,
          tagOpt = query.filter.tag,
          offset = query.skip,
          limit = query.limit,
          resolutionMethod = query.filter.resolutionMethod
        )
        .transactWallet(xa)
        .orDie
      entries = filteredRows.map(CredentialDefinitionRow.toModel)

      filteredRowsCount <- CredentialDefinitionSql
        .lookupCount(
          authorOpt = query.filter.author,
          nameOpt = query.filter.name,
          versionOpt = query.filter.version,
          tagOpt = query.filter.tag,
          resolutionMethod = query.filter.resolutionMethod
        )
        .transactWallet(xa)
        .orDie

      totalRowsCount <- CredentialDefinitionSql.totalCount.transactWallet(xa).orDie
    } yield SearchResult(entries, filteredRowsCount, totalRowsCount)
  }
}

object JdbcCredentialDefinitionRepository {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], JdbcCredentialDefinitionRepository] =
    ZLayer.fromFunction(JdbcCredentialDefinitionRepository.apply)
}
