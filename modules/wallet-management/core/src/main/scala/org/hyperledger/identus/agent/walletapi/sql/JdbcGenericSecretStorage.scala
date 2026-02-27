package org.hyperledger.identus.agent.walletapi.sql

import doobie.*
import io.getquill.JsonValue
import org.hyperledger.identus.agent.walletapi.sql.model.GenericSecretSql
import org.hyperledger.identus.agent.walletapi.sql.model as db
import org.hyperledger.identus.agent.walletapi.storage.{GenericSecret, GenericSecretStorage}
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

class JdbcGenericSecretStorage(xa: Transactor[ContextAwareTask]) extends GenericSecretStorage {

  override def set[K, V](key: K, secret: V)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Unit] = {
    val keyPath = ev.keyPath(key)
    val payload = ev.encodeValue(secret)
    for {
      now <- Clock.instant
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      s = db.GenericSecret(key = keyPath, payload = JsonValue(payload), createdAt = now, walletId = walletId)
      _ <- GenericSecretSql
        .insert(s)
        .transactWallet(xa)
    } yield ()
  }

  override def get[K, V](key: K)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Option[V]] = {
    val keyPath = ev.keyPath(key)
    GenericSecretSql
      .findByKey(keyPath)
      .transactWallet(xa)
      .flatMap(
        _.headOption
          .fold(ZIO.none)(row => ZIO.fromTry(ev.decodeValue(row.payload.value)).asSome)
      )
  }

}

object JdbcGenericSecretStorage {
  val layer: URLayer[Transactor[ContextAwareTask], JdbcGenericSecretStorage] =
    ZLayer.fromFunction(new JdbcGenericSecretStorage(_))
}
