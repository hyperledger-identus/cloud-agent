package org.hyperledger.identus.pollux.sql.repository

import doobie.*
import doobie.implicits.*
import doobie.util.transactor.Transactor
import org.hyperledger.identus.pollux.core.repository.PresentationExchangeRepository
import org.hyperledger.identus.pollux.prex.PresentationDefinition
import org.hyperledger.identus.pollux.sql.model.db
import org.hyperledger.identus.pollux.sql.model.db.PresentationDefinitionSql
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.interop.catz.*

import java.util.UUID

class JdbcPresentationExchangeRepository(xa: Transactor[ContextAwareTask], xb: Transactor[Task])
    extends PresentationExchangeRepository {

  override def createPresentationDefinition(pd: PresentationDefinition): URIO[WalletAccessContext, Unit] = {
    for {
      now <- Clock.instant
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      row = db.PresentationDefinition.fromModel(pd, walletId, now)
      _ <- PresentationDefinitionSql
        .insert(row)
        .transactWallet(xa)
        .orDie
    } yield ()
  }

  override def findPresentationDefinition(id: UUID): UIO[Option[PresentationDefinition]] = {
    PresentationDefinitionSql
      .findById(id)
      .transact(xb)
      .orDie
      .map(_.headOption.map(_.toModel))
  }

  override def listPresentationDefinition(
      offset: Option[Int],
      limit: Option[Int]
  ): URIO[WalletAccessContext, (Seq[PresentationDefinition], Int)] = {
    val countCxnIO = PresentationDefinitionSql.lookupCount()
    val pdCxnIO = PresentationDefinitionSql.lookup(
      offset = offset.getOrElse(0),
      limit = limit.getOrElse(100)
    )

    val effect =
      for {
        totalCount <- countCxnIO
        rows <- pdCxnIO.map(_.map(_.toModel))
      } yield (rows, totalCount.toInt)

    effect
      .transactWallet(xa)
      .orDie
  }

}

object JdbcPresentationExchangeRepository {
  def layer: URLayer[Transactor[ContextAwareTask] & Transactor[Task], PresentationExchangeRepository] =
    ZLayer.fromFunction(JdbcPresentationExchangeRepository(_, _))
}
