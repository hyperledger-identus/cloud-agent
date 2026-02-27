package org.hyperledger.identus.pollux.sql.repository

import doobie.*
import doobie.implicits.*
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.core.repository.{CredentialSchemaRepository, Repository}
import org.hyperledger.identus.pollux.core.repository.Repository.*
import org.hyperledger.identus.pollux.sql.model.db.{CredentialSchema as CredentialSchemaRow, CredentialSchemaSql}
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.interop.catz.*

import java.util.UUID

case class JdbcCredentialSchemaRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task])
    extends CredentialSchemaRepository {
  import CredentialSchemaSql.*
  override def create(cs: CredentialSchema): URIO[WalletAccessContext, CredentialSchema] = {
    ZIO.serviceWithZIO[WalletAccessContext](ctx =>
      CredentialSchemaSql
        .insert(CredentialSchemaRow.fromModel(cs, ctx.walletId))
        .transactWallet(xa)
        .orDie
        .map(CredentialSchemaRow.toModel)
    )
  }

  override def findByGuid(guid: UUID, resolutionMethod: ResourceResolutionMethod): UIO[Option[CredentialSchema]] = {
    CredentialSchemaSql
      .findByGUID(guid, resolutionMethod)
      .transact(xb)
      .orDie
      .map(
        _.headOption
          .map(CredentialSchemaRow.toModel)
      )
  }

  // NOTE: this function is not used anywhere
  override def update(cs: CredentialSchema): URIO[WalletAccessContext, CredentialSchema] = {
    ZIO.serviceWithZIO[WalletAccessContext](ctx =>
      CredentialSchemaSql
        .update(CredentialSchemaRow.fromModel(cs, ctx.walletId))
        .transactWallet(xa)
        .orDie
        .map(CredentialSchemaRow.toModel)
    )
  }

  def getAllVersions(
      id: UUID,
      author: String,
      resolutionMethod: ResourceResolutionMethod
  ): URIO[WalletAccessContext, List[CredentialSchema]] = {
    CredentialSchemaSql
      .getAllVersions(id, author, resolutionMethod)
      .transactWallet(xa)
      .orDie
      .map(_.map(CredentialSchemaRow.toModel))
  }

  // NOTE: this function is not used anywhere
  override def delete(guid: UUID): URIO[WalletAccessContext, CredentialSchema] = {
    CredentialSchemaSql
      .delete(guid)
      .transactWallet(xa)
      .orDie
      .map(CredentialSchemaRow.toModel)
  }

  def deleteAll(): URIO[WalletAccessContext, Unit] = {
    CredentialSchemaSql.deleteAll
      .transactWallet(xa)
      .orDie
      .flatMap(count => ZIO.unit)
  }

  override def search(
      query: SearchQuery[CredentialSchema.Filter]
  ): URIO[WalletAccessContext, SearchResult[CredentialSchema]] = {
    for {
      filteredRows <- CredentialSchemaSql
        .lookup(
          authorOpt = query.filter.author,
          nameOpt = query.filter.name,
          versionOpt = query.filter.version,
          tagOpt = query.filter.tags,
          offset = query.skip,
          limit = query.limit,
          resolutionMethod = query.filter.resolutionMethod
        )
        .transactWallet(xa)
        .orDie
      entries = filteredRows.map(CredentialSchemaRow.toModel)

      filteredRowsCount <- CredentialSchemaSql
        .lookupCount(
          authorOpt = query.filter.author,
          nameOpt = query.filter.name,
          versionOpt = query.filter.version,
          tagOpt = query.filter.tags,
          resolutionMethod = query.filter.resolutionMethod
        )
        .transactWallet(xa)
        .orDie
      totalRowsCount <- CredentialSchemaSql.totalCount.transactWallet(xa).orDie
    } yield SearchResult(entries, filteredRowsCount, totalRowsCount)
  }
}

object JdbcCredentialSchemaRepository {
  val layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], CredentialSchemaRepository] =
    ZLayer.fromFunction(JdbcCredentialSchemaRepository.apply)
}
