package org.hyperledger.identus.agent.walletapi.sql.model

import io.getquill.{SnakeCase, *}
import io.getquill.context.json.PostgresJsonExtensions
import io.getquill.doobie.DoobieContext
import org.hyperledger.identus.shared.models.WalletId
import zio.json.ast.Json

import java.time.Instant

final case class GenericSecret(
    key: String,
    payload: JsonValue[Json],
    createdAt: Instant,
    walletId: WalletId
)

object GenericSecretSql extends DoobieContext.Postgres(SnakeCase), PostgresJsonExtensions {
  def insert(secret: GenericSecret) = run {
    quote(
      query[GenericSecret].insertValue(lift(secret))
    )
  }

  def findByKey(key: String) = run {
    quote(
      query[GenericSecret].filter(_.key == lift(key)).take(1)
    )
  }
}
