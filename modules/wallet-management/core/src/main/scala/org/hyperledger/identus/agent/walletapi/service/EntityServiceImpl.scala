package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.error.EntityServiceError
import org.hyperledger.identus.agent.walletapi.model.error.EntityServiceError.{EntityNotFound, WalletNotFound}
import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.agent.walletapi.sql.EntityRepository
import org.hyperledger.identus.shared.models.{WalletAdministrationContext, WalletId}
import zio.{IO, UIO, URLayer, ZLayer}

import java.util.UUID

class EntityServiceImpl(repository: EntityRepository, walletManagementService: WalletManagementService)
    extends EntityService {
  def create(entity: Entity): IO[WalletNotFound, Entity] = {
    for {
      _ <- walletManagementService
        .findWallet(WalletId.fromUUID(entity.walletId))
        .someOrFail(WalletNotFound(entity.walletId))
        .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      entity <- repository.insert(entity)
    } yield entity

  }

  def getById(entityId: UUID): IO[EntityNotFound, Entity] = {
    repository
      .findById(entityId)
      .someOrFail(EntityNotFound(entityId))
  }

  override def getAll(offset: Option[Int], limit: Option[Int]): UIO[Seq[Entity]] = {
    repository.getAll(offset.getOrElse(0), limit.getOrElse(100))
  }

  def deleteById(entityId: UUID): IO[EntityNotFound, Unit] = {
    for {
      _ <- getById(entityId)
      _ <- repository.delete(entityId)
    } yield ()
  }

  override def updateName(entityId: UUID, name: String): IO[EntityNotFound, Unit] = {
    for {
      _ <- getById(entityId)
      _ <- repository.updateName(entityId, name)
    } yield ()
  }

  override def assignWallet(entityId: UUID, walletId: UUID): IO[EntityNotFound | WalletNotFound, Unit] = {
    for {
      _ <- walletManagementService
        .findWallet(WalletId.fromUUID(walletId))
        .someOrFail(WalletNotFound(walletId))
        .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      _ <- getById(entityId)
      _ <- repository.updateWallet(entityId, walletId)
    } yield ()
  }
}

object EntityServiceImpl {
  val layer: URLayer[EntityRepository & WalletManagementService, EntityService] =
    ZLayer.fromFunction(new EntityServiceImpl(_, _))
}
