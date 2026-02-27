package org.hyperledger.identus.agent.walletapi.vault

import com.nimbusds.jose.jwk.OctetKeyPair
import org.hyperledger.identus.agent.walletapi.storage.DIDSecretStorage
import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.crypto.jwk.{FromJWK, JWK}
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.models.{HexString, KeyId, WalletAccessContext, WalletId}
import zio.*

import java.nio.charset.StandardCharsets

class VaultDIDSecretStorage(vaultKV: VaultKVClient, useSemanticPath: Boolean) extends DIDSecretStorage {

  override def insertKey(did: DidId, keyId: KeyId, keyPair: OctetKeyPair): RIO[WalletAccessContext, Int] = {
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

  override def getKey(did: DidId, keyId: KeyId): RIO[WalletAccessContext, Option[OctetKeyPair]] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, _) = peerDidKeyPath(walletId)(did, keyId)
      keyPair <- vaultKV.get[OctetKeyPair](path)
    } yield keyPair
  }

  override def insertPrismDIDKeyPair[K](
      did: PrismDID,
      keyId: KeyId,
      operationHash: Array[Byte],
      keyPair: K
  )(using c: Conversion[K, JWK]): URIO[WalletAccessContext, Unit] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, metadata) = prismDIDKeyPath(walletId)(did, keyId, operationHash)
      alreadyExist <- vaultKV.get[JWK](path).map(_.isDefined)
      jwk = c(keyPair)
      _ <- vaultKV
        .set[JWK](path, jwk, metadata)
        .when(!alreadyExist)
        .someOrFail(Exception(s"Secret on path $path already exists."))
    } yield ()
  }.orDie

  override def getPrismDIDKeyPair[K](did: PrismDID, keyId: KeyId, operationHash: Array[Byte])(using
      c: FromJWK[K]
  ): URIO[WalletAccessContext, Option[K]] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      (path, _) = prismDIDKeyPath(walletId)(did, keyId, operationHash: Array[Byte])
      keyPair <- vaultKV.get[JWK](path).flatMap {
        case None      => ZIO.none
        case Some(jwk) => ZIO.fromEither(c.from(jwk)).mapError(Exception(_)).asSome
      }
    } yield keyPair
  }.orDie

  /** @return A tuple of secret path and a secret custom_metadata */
  private def peerDidKeyPath(walletId: WalletId)(did: DidId, keyId: KeyId): (String, Map[String, String]) = {
    val basePath = s"${walletBasePath(walletId)}/dids/peer"
    val relativePath = s"${did.value}/keys/${keyId.value}"
    if (useSemanticPath) {
      s"$basePath/$relativePath" -> Map.empty
    } else {
      val relativePathHash = Sha256Hash.compute(relativePath.getBytes(StandardCharsets.UTF_8)).hexEncoded
      s"$basePath/$relativePathHash" -> Map(SEMANTIC_PATH_METADATA_KEY -> relativePath)
    }
  }

  /** @return A tuple of secret path and a secret custom_metadata */
  private def prismDIDKeyPath(
      walletId: WalletId
  )(did: PrismDID, keyId: KeyId, operationHash: Array[Byte]): (String, Map[String, String]) = {
    val basePath = s"${walletBasePath(walletId)}/dids/prism"
    val relativePath = s"${did.asCanonical}/keys/${keyId.value}/${HexString.fromByteArray(operationHash)}"
    if (useSemanticPath) {
      s"$basePath/$relativePath" -> Map.empty
    } else {
      val relativePathHash = Sha256Hash.compute(relativePath.getBytes(StandardCharsets.UTF_8)).hexEncoded
      s"$basePath/$relativePathHash" -> Map(SEMANTIC_PATH_METADATA_KEY -> relativePath)
    }
  }
}

object VaultDIDSecretStorage {
  def layer(useSemanticPath: Boolean): URLayer[VaultKVClient, DIDSecretStorage] =
    ZLayer.fromFunction(VaultDIDSecretStorage(_, useSemanticPath))
}
