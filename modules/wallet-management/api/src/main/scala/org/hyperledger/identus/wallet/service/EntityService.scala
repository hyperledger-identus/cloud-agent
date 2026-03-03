package org.hyperledger.identus.wallet.service

import org.hyperledger.identus.wallet.model.error.EntityServiceError
import org.hyperledger.identus.wallet.model.error.EntityServiceError.{EntityNotFound, WalletNotFound}
import org.hyperledger.identus.wallet.model.Entity
import zio.{IO, UIO}

import java.util.UUID

trait EntityService {
  def create(entity: Entity): IO[WalletNotFound, Entity]

  def getById(entityId: UUID): IO[EntityNotFound, Entity]

  def getAll(offset: Option[Int], limit: Option[Int]): UIO[Seq[Entity]]

  def deleteById(entityId: UUID): IO[EntityNotFound, Unit]

  def updateName(entityId: UUID, name: String): IO[EntityNotFound, Unit]

  def assignWallet(entityId: UUID, walletId: UUID): IO[EntityNotFound | WalletNotFound, Unit]
}
