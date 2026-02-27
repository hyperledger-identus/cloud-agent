package org.hyperledger.identus.agent.walletapi.vault

import org.hyperledger.identus.agent.walletapi.storage.{GenericSecret, GenericSecretStorage}
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets

class VaultGenericSecretStorage(vaultKV: VaultKVClient, useSemanticPath: Boolean) extends GenericSecretStorage {

  override def set[K, V](key: K, secret: V)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Unit] = {
    val payload = ev.encodeValue(secret)
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, metadata) = constructKeyPath(walletId)(key)
      alreadyExist <- vaultKV.get[Json](path).map(_.isDefined)
      _ <- vaultKV
        .set[Json](path, payload, metadata)
        .when(!alreadyExist)
        .someOrFail(Exception(s"Secret on path $path already exists."))
    } yield ()
  }

  override def get[K, V](key: K)(implicit ev: GenericSecret[K, V]): RIO[WalletAccessContext, Option[V]] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, _) = constructKeyPath(walletId)(key)
      json <- vaultKV.get[Json](path)
      result <- json.fold(ZIO.none)(json => ZIO.fromTry(ev.decodeValue(json)).asSome)
    } yield result
  }

  /** @return A tuple of secret path and a secret custom_metadata */
  private def constructKeyPath[K, V](
      walletId: WalletId
  )(key: K)(implicit ev: GenericSecret[K, V]): (String, Map[String, String]) = {
    val basePath = s"${walletBasePath(walletId)}/generic-secrets"
    val relativePath = ev.keyPath(key)
    if (useSemanticPath) {
      s"$basePath/$relativePath" -> Map.empty
    } else {
      val relativePathHash = Sha256Hash.compute(relativePath.getBytes(StandardCharsets.UTF_8)).hexEncoded
      s"$basePath/$relativePathHash" -> Map(SEMANTIC_PATH_METADATA_KEY -> relativePath)
    }
  }

}

object VaultGenericSecretStorage {
  def layer(useSemanticPath: Boolean): URLayer[VaultKVClient, GenericSecretStorage] =
    ZLayer.fromFunction(VaultGenericSecretStorage(_, useSemanticPath))
}
