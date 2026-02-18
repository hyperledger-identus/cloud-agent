package org.hyperledger.identus.vdr

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.agent.vdr.{VdrOperationSigner, VdrServiceError}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.castor.core.model.did.{CanonicalPrismDID, PrismDID}
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import org.hyperledger.identus.shared.models.HexString
import zio.*

import scala.util.Random

/** Signs prism-node VDR operations using the wallet's managed DID and VDR internal key */
final class PrismNodeVdrOperationSigner(
    managedDIDService: ManagedDIDService,
    defaultVdrKeyId: KeyId = KeyId("vdr-1"),
    maxDidScan: Int = 200
) extends VdrOperationSigner {

  private def parseDidAndKey(
      didKeyId: Option[String]
  ): (Option[CanonicalPrismDID], KeyId) =
    didKeyId match {
      case Some(full) if full.contains("#") =>
        val Array(didStr, keyStr) = full.split("#", 2)
        val suffix = didStr.split(":").lastOption
        val didOpt = suffix.flatMap(s => PrismDID.buildCanonicalFromSuffix(s).toOption)
        (didOpt, KeyId(keyStr))
      case other =>
        (None, KeyId(other.getOrElse(defaultVdrKeyId.value)))
    }

  private def selectDid(
      didKeyId: KeyId,
      explicitDid: Option[CanonicalPrismDID]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, CanonicalPrismDID] =
    explicitDid match {
      case Some(did) =>
        managedDIDService
          .findDIDKeyPair(did, didKeyId)
          .flatMap {
            case Some(_) =>
              logDidKeys(did) *> ZIO.succeed(did)
            case None =>
              ZIO.logDebug(s"[vdr signer] VDR key '${didKeyId.value}' not found on DID ${did.toString}") *>
                ZIO.fail(
                  VdrServiceError.MissingVdrKey(
                    new Exception("Requested VDR key not found on the selected DID")
                  )
                )
          }
      case None =>
        for {
          allDids <- managedDIDService
            .listManagedDIDPage(offset = 0, limit = maxDidScan)
            .mapError(err => VdrServiceError.MissingVdrKey(new Exception(err.toString)))
            .map(_._1.map(_.did))

          matchesWithFlags <- ZIO.foreach(allDids) { did =>
            managedDIDService
              .findDIDKeyPair(did, didKeyId)
              .map(found => did -> found.nonEmpty)
          }

          _ <- ZIO.logInfo(
            s"[vdr signer] scanning DIDs for key '${didKeyId.value}': " +
              matchesWithFlags.map { case (d, has) => s"${d.toString} -> $has" }.mkString(", ")
          )

          matches = matchesWithFlags.collect { case (d, true) => d }

          result <- matches match {
            case Nil =>
              ZIO.fail(
                VdrServiceError.MissingVdrKey(
                  new Exception("Requested VDR key not found on any managed DID")
                )
              )
            case single :: Nil => logDidKeys(single) *> ZIO.succeed(single)
            case many          =>
              ZIO.fail(
                VdrServiceError.MissingVdrKey(
                  new Exception(
                    "Requested VDR key is present on multiple managed DIDs; specify DID explicitly"
                  )
                )
              )
          }
        } yield result
    }

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
          ZIO.fail(
            VdrServiceError.MissingVdrKey(
              new Exception("Requested VDR key not found on the selected DID")
            )
          )
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

  private def logDidKeys(did: CanonicalPrismDID): ZIO[WalletAccessContext, Nothing, Unit] =
    managedDIDService
      .getManagedDIDState(did)
      .either
      .flatMap {
        case Right(Some(_)) =>
          ZIO.logInfo(s"[vdr signer] DID ${did.toString} state loaded (keys not expanded in model)")
        case Right(None) =>
          ZIO.logWarning(s"[vdr signer] DID state missing for ${did.toString}")
        case Left(err) =>
          ZIO.logWarning(s"[vdr signer] failed to fetch DID state for ${did.toString}: ${err.toString}")
      }

  override def signCreate(
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation] =
    for {
      parsed <- ZIO.succeed(parseDidAndKey(didKeyId))
      did <- selectDid(parsed._2, parsed._1)
      key <- resolveKey(did, Some(parsed._2.value))
      _ <- ZIO.logInfo(
        s"[vdr signer] signCreate did=${did.toString} key=${parsed._2.value} bytes=${data.length}"
      )
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
      parsed <- ZIO.succeed(parseDidAndKey(didKeyId))
      did <- selectDid(parsed._2, parsed._1)
      key <- resolveKey(did, Some(parsed._2.value))
      _ <- ZIO.logInfo(
        s"[vdr signer] signUpdate did=${did.toString} key=${parsed._2.value} prevHash=${HexString.fromByteArray(previousEventHash)} bytes=${data.length}"
      )
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
      parsed <- ZIO.succeed(parseDidAndKey(didKeyId))
      did <- selectDid(parsed._2, parsed._1)
      key <- resolveKey(did, Some(parsed._2.value))
      _ <- ZIO.logInfo(
        s"[vdr signer] signDeactivate did=${did.toString} key=${parsed._2.value} prevHash=${HexString.fromByteArray(previousEventHash)}"
      )
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
