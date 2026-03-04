package org.hyperledger.identus.shared.db

import doobie.util.transactor.Transactor
import zio.*

enum PersistenceType:
  case PostgreSQL, SQLite

trait PersistenceProvider:
  def providerType: PersistenceType
  def transactor: Transactor[Task]
  def migrate: IO[Throwable, Unit]
