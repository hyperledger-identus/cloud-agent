package org.hyperledger.identus.agent.walletapi.sql

import doobie.*
import doobie.implicits.*
import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.shared.db.Implicits.ensureOneAffectedRowOrDie
import zio.*
import zio.interop.catz.*

import java.util.UUID

class JdbcEntityRepository(xa: Transactor[Task]) extends EntityRepository {
  import EntityStorageSql.*
  override def insert(entity: Entity): UIO[Entity] = {
    EntityStorageSql
      .insert(model2db(entity))
      .transact(xa)
      .map(db2model)
      .orDie
  }
  override def getById(id: UUID): UIO[Entity] = {
    EntityStorageSql
      .getById(id)
      .transact(xa)
      .map(_.headOption.map(db2model))
      .someOrElseZIO(ZIO.dieMessage(s"Entity not found: id=$id"))
      .orDie
  }

  override def findById(id: UUID): UIO[Option[Entity]] = {
    EntityStorageSql
      .getById(id)
      .transact(xa)
      .map(_.headOption.map(db2model))
      .orDie
  }

  override def updateName(entityId: UUID, name: String): UIO[Unit] = {
    EntityStorageSql
      .updateName(entityId, name)
      .transact(xa)
      .ensureOneAffectedRowOrDie
  }

  override def updateWallet(entityId: UUID, walletId: UUID): UIO[Unit] = {
    EntityStorageSql
      .updateWallet(entityId, walletId)
      .transact(xa)
      .ensureOneAffectedRowOrDie
  }

  override def delete(entityId: UUID): UIO[Unit] = {
    EntityStorageSql
      .delete(entityId)
      .transact(xa)
      .ensureOneAffectedRowOrDie
  }

  override def getAll(offset: Index, limit: Index): UIO[List[Entity]] = {
    EntityStorageSql
      .getAll(offset, limit)
      .transact(xa)
      .map(_.map(db2model))
      .orDie
  }
}

object JdbcEntityRepository {
  val layer: URLayer[Transactor[Task], EntityRepository] =
    ZLayer.fromFunction(new JdbcEntityRepository(_))
}
