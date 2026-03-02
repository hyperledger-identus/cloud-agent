package org.hyperledger.identus.vdr

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.did.api.{DIDKeySigner, DIDKeySignerError}
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import org.hyperledger.identus.shared.models.HexString
import zio.*

import scala.util.Random

/** Signs prism-node VDR operations using the wallet's managed DID and VDR internal key */
final class PrismNodeVdrOperationSigner(
    didKeySigner: DIDKeySigner,
    defaultVdrKeyId: KeyId = KeyId("vdr-1"),
    maxDidScan: Int = 200
) extends VdrOperationSigner {

  private def mapError(
      e: DIDKeySignerError
  ): VdrServiceError.MissingVdrKey | VdrServiceError.DeactivatedDid =
    e match {
      case DIDKeySignerError.DIDDeactivated(msg) => VdrServiceError.DeactivatedDid(new Exception(msg))
      case DIDKeySignerError.KeyNotFound(msg)    => VdrServiceError.MissingVdrKey(new Exception(msg))
      case DIDKeySignerError.AmbiguousDID(msg)   => VdrServiceError.MissingVdrKey(new Exception(msg))
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
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.MissingVdrKey | VdrServiceError.DeactivatedDid,
    node_models.SignedAtalaOperation
  ] =
    for {
      ctx <- didKeySigner.resolveSigningKey(didKeyId, defaultVdrKeyId, maxDidScan).mapError(mapError)
      _ <- ZIO.logInfo(
        s"[vdr signer] signCreate did=${ctx.did.toString} key=${ctx.keyId.value} bytes=${data.length}"
      )
      op = node_models
        .AtalaOperation()
        .withCreateStorageEntry(
          node_models.CreateStorageEntryOperation(
            didPrismHash = ByteString.copyFrom(ctx.did.stateHash.toByteArray),
            nonce = ByteString.copyFrom(Random.nextBytes(16)),
            data = node_models.CreateStorageEntryOperation.Data.Bytes(ByteString.copyFrom(data))
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), ctx.keyPair)

  override def signUpdate(
      previousEventHash: Array[Byte],
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.MissingVdrKey | VdrServiceError.DeactivatedDid,
    node_models.SignedAtalaOperation
  ] =
    for {
      ctx <- didKeySigner.resolveSigningKey(didKeyId, defaultVdrKeyId, maxDidScan).mapError(mapError)
      _ <- ZIO.logInfo(
        s"[vdr signer] signUpdate did=${ctx.did.toString} key=${ctx.keyId.value} prevHash=${HexString.fromByteArray(previousEventHash)} bytes=${data.length}"
      )
      op = node_models
        .AtalaOperation()
        .withUpdateStorageEntry(
          node_models.UpdateStorageEntryOperation(
            previousEventHash = ByteString.copyFrom(previousEventHash),
            data = node_models.UpdateStorageEntryOperation.Data.Bytes(ByteString.copyFrom(data))
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), ctx.keyPair)

  override def signDeactivate(
      previousEventHash: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[
    WalletAccessContext,
    VdrServiceError.MissingVdrKey | VdrServiceError.DeactivatedDid,
    node_models.SignedAtalaOperation
  ] =
    for {
      ctx <- didKeySigner.resolveSigningKey(didKeyId, defaultVdrKeyId, maxDidScan).mapError(mapError)
      _ <- ZIO.logInfo(
        s"[vdr signer] signDeactivate did=${ctx.did.toString} key=${ctx.keyId.value} prevHash=${HexString.fromByteArray(previousEventHash)}"
      )
      op = node_models
        .AtalaOperation()
        .withDeactivateStorageEntry(
          node_models.DeactivateStorageEntryOperation(
            previousEventHash = ByteString.copyFrom(previousEventHash)
          )
        )
    } yield sign(op, didKeyId.getOrElse(defaultVdrKeyId.value), ctx.keyPair)
}

object PrismNodeVdrOperationSigner {
  val layer: URLayer[DIDKeySigner, VdrOperationSigner] =
    ZLayer.fromFunction(new PrismNodeVdrOperationSigner(_))
}
