package io.iohk.atala.agent.walletapi.vault

import com.nimbusds.jose.jwk.OctetKeyPair
import io.iohk.atala.agent.walletapi.storage.DIDSecretStorage
import io.iohk.atala.mercury.model.DidId
import io.iohk.atala.prism.crypto.Sha256
import io.iohk.atala.shared.models.WalletAccessContext
import io.iohk.atala.shared.models.WalletId
import zio.*

import java.nio.charset.StandardCharsets

class VaultDIDSecretStorage(vaultKV: VaultKVClient, useSemanticPath: Boolean) extends DIDSecretStorage {

  override def insertKey(did: DidId, keyId: String, keyPair: OctetKeyPair): RIO[WalletAccessContext, Int] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, metadata) = peerDidKeyPath(walletId)(did, keyId)
      alreadyExist <- vaultKV.get[OctetKeyPair](path).map(_.isDefined)
      _ <- vaultKV
        .set[OctetKeyPair](path, keyPair, metadata)
        .when(!alreadyExist)
        .someOrFail(Exception(s"Secret on path $path already exists."))
    } yield 1
  }

  override def getKey(did: DidId, keyId: String): RIO[WalletAccessContext, Option[OctetKeyPair]] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, _) = peerDidKeyPath(walletId)(did, keyId)
      keyPair <- vaultKV.get[OctetKeyPair](path)
    } yield keyPair
  }

  /** @return A tuple of secret path and a secret custom_metadata */
  private def peerDidKeyPath(walletId: WalletId)(did: DidId, keyId: String): (String, Map[String, String]) = {
    val basePath = s"${walletBasePath(walletId)}/dids/peer"
    val relativePath = s"${did.value}/keys/$keyId"
    if (useSemanticPath) {
      s"$basePath/$relativePath" -> Map.empty
    } else {
      val relativePathHash = Sha256.compute(relativePath.getBytes(StandardCharsets.UTF_8)).getHexValue()
      s"$basePath/$relativePathHash" -> Map(SEMANTIC_PATH_METADATA_KEY -> relativePath)
    }
  }
}

object VaultDIDSecretStorage {
  def layer(useSemanticPath: Boolean): URLayer[VaultKVClient, DIDSecretStorage] =
    ZLayer.fromFunction(VaultDIDSecretStorage(_, useSemanticPath))
}
