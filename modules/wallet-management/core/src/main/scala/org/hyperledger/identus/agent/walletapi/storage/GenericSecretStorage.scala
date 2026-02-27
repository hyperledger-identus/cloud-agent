package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.json.ast.Json

import scala.util.Try

trait GenericSecretStorage {
  def set[K, V](key: K, secret: V)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Unit]
  def get[K, V](key: K)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Option[V]]
}

trait GenericSecret[K, V] {
  def keyPath(id: K): String
  def encodeValue(secret: V): Json
  def decodeValue(json: Json): Try[V]
}
