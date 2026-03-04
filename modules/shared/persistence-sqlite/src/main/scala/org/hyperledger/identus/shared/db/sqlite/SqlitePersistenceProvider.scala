package org.hyperledger.identus.shared.db.sqlite

import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import org.hyperledger.identus.shared.db.{PersistenceProvider, PersistenceType}
import zio.*
import zio.interop.catz.*

class SqlitePersistenceProvider(
    jdbcUrl: String,
    xa: Transactor[Task],
) extends PersistenceProvider:

  override def providerType: PersistenceType = PersistenceType.SQLite

  override def transactor: Transactor[Task] = xa

  override def migrate: IO[Throwable, Unit] =
    ZIO.attempt {
      Flyway
        .configure()
        .dataSource(jdbcUrl, "", "")
        .locations("classpath:db/migration/sqlite")
        .load()
        .migrate()
    }.unit

object SqlitePersistenceProvider:

  def file(path: String): ZIO[Scope, Throwable, SqlitePersistenceProvider] =
    val jdbcUrl = s"jdbc:sqlite:$path"
    for
      xa <- ZIO.attempt {
        Transactor.fromDriverManager[Task](
          driver = "org.sqlite.JDBC",
          url = jdbcUrl,
          user = "",
          password = "",
          logHandler = None,
        )
      }
    yield SqlitePersistenceProvider(jdbcUrl, xa)
