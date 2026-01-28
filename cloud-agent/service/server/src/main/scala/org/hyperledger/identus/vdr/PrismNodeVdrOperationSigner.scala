package org.hyperledger.identus.vdr

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.agent.vdr.{VdrOperationSigner, VdrServiceError}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.castor.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

import scala.util.Random

/** Signs prism-node VDR operations using the wallet's managed DID and VDR internal key */
final class PrismNodeVdrOperationSigner(managedDIDService: ManagedDIDService) extends VdrOperationSigner {

  private val defaultVdrKeyId = KeyId("vdr-1")

  private def selectDid: ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, CanonicalPrismDID] =
    managedDIDService
      .listManagedDIDPage(offset = 0, limit = 1)
      .mapError(err => VdrServiceError.MissingVdrKey(new Exception(err.toString)))
      .map(_._1.headOption.map(_.did))
      .someOrFail(VdrServiceError.MissingVdrKey(new Exception("No managed DID found")))

  private def resolveKey(
      did: CanonicalPrismDID,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, Secp256k1KeyPair] =
    managedDIDService
      .findDIDKeyPair(did, KeyId(didKeyId.getOrElse(defaultVdrKeyId.value)))
      .flatMap {
        case Some(key: Secp256k1KeyPair) => ZIO.succeed(key)
        case Some(_)                     =>
          ZIO.fail(VdrServiceError.MissingVdrKey(new Exception("VDR key is not secp256k1")))
        case None =>
          ZIO.fail(VdrServiceError.MissingVdrKey(new Exception("VDR key not found")))
      }

  private def sign(
      op: node_models.AtalaOperation,
      keyId: String,
      key: Secp256k1KeyPair
  ): node_models.SignedAtalaOperation =
    node_models.SignedAtalaOperation(
      signedWith = keyId,
      operation = Some(op),
      signature = ByteString.copyFrom(key.privateKey.sign(op.toByteArray))
    )

  override def signCreate(
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] =
    for {
      did <- selectDid
      key <- resolveKey(did, didKeyId)
      op = node_models
        .AtalaOperation()
        .withCreateStorageEntry(
          node_models.CreateStorageEntryOperation(
            didPrismHash = ByteString.copyFrom(did.stateHash.toByteArray),
            nonce = ByteString.copyFrom(Random.nextBytes(16)),
            data = Some(node_models.StorageData(node_models.StorageData.Content.Bytes(ByteString.copyFrom(data))))
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), key)

  override def signUpdate(
      previousEventHash: Array[Byte],
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] =
    for {
      did <- selectDid
      key <- resolveKey(did, didKeyId)
      op = node_models
        .AtalaOperation()
        .withUpdateStorageEntry(
          node_models.UpdateStorageEntryOperation(
            previousEventHash = ByteString.copyFrom(previousEventHash),
            data = Some(node_models.StorageData(node_models.StorageData.Content.Bytes(ByteString.copyFrom(data))))
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), key)

  override def signDeactivate(
      previousEventHash: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] =
    for {
      did <- selectDid
      key <- resolveKey(did, didKeyId)
      op = node_models
        .AtalaOperation()
        .withDeactivateStorageEntry(
          node_models.DeactivateStorageEntryOperation(
            previousEventHash = ByteString.copyFrom(previousEventHash)
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), key)
}

object PrismNodeVdrOperationSigner {
  val layer: URLayer[ManagedDIDService, VdrOperationSigner] =
    ZLayer.fromFunction(new PrismNodeVdrOperationSigner(_))
}
