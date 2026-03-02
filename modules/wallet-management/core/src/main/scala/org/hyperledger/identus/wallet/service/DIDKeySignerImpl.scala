package org.hyperledger.identus.wallet.service

import org.hyperledger.identus.did.api.{DIDKeySigner, DIDKeySignerError, DIDSigningContext}
import org.hyperledger.identus.did.core.model.did.{CanonicalPrismDID, PrismDID}
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

final class DIDKeySignerImpl(managedDIDService: ManagedDIDService) extends DIDKeySigner {

  override def resolveSigningKey(
      didKeyId: Option[String],
      defaultKeyId: KeyId,
      maxScan: Int = 200
  ): ZIO[WalletAccessContext, DIDKeySignerError, DIDSigningContext] =
    for {
      parsed <- ZIO.succeed(parseDidAndKey(didKeyId, defaultKeyId))
      (explicitDid, keyId) = parsed
      did <- selectDid(keyId, explicitDid, maxScan)
      _ <- ensureDidActive(did)
      key <- resolveKey(did, keyId)
    } yield DIDSigningContext(did, keyId, key)

  private def parseDidAndKey(
      didKeyId: Option[String],
      defaultKeyId: KeyId
  ): (Option[CanonicalPrismDID], KeyId) =
    didKeyId match {
      case Some(full) if full.contains("#") =>
        val Array(didStr, keyStr) = full.split("#", 2)
        val suffix = didStr.split(":").lastOption
        val didOpt = suffix.flatMap(s => PrismDID.buildCanonicalFromSuffix(s).toOption)
        (didOpt, KeyId(keyStr))
      case other =>
        (None, KeyId(other.getOrElse(defaultKeyId.value)))
    }

  private def selectDid(
      keyId: KeyId,
      explicitDid: Option[CanonicalPrismDID],
      maxScan: Int
  ): ZIO[WalletAccessContext, DIDKeySignerError.KeyNotFound, CanonicalPrismDID] =
    explicitDid match {
      case Some(did) =>
        managedDIDService
          .findDIDKeyPair(did, keyId)
          .flatMap {
            case Some(_) => ZIO.succeed(did)
            case None    =>
              ZIO.logDebug(s"[DIDKeySigner] key '${keyId.value}' not found on DID ${did.toString}") *>
                ZIO.fail(DIDKeySignerError.KeyNotFound(s"Key '${keyId.value}' not found on DID ${did.toString}"))
          }
      case None =>
        for {
          allDids <- managedDIDService
            .listManagedDIDPage(offset = 0, limit = maxScan)
            .mapError(err => DIDKeySignerError.KeyNotFound(err.toString))
            .map(_._1.map(_.did))
          matchesWithFlags <- ZIO.foreach(allDids) { did =>
            managedDIDService.findDIDKeyPair(did, keyId).map(found => did -> found.nonEmpty)
          }
          _ <- ZIO.logInfo(
            s"[DIDKeySigner] scanning DIDs for key '${keyId.value}': " +
              matchesWithFlags.map { case (d, has) => s"${d.toString} -> $has" }.mkString(", ")
          )
          matches = matchesWithFlags.collect { case (d, true) => d }
          result <- matches match {
            case Nil =>
              ZIO.fail(DIDKeySignerError.KeyNotFound(s"Key '${keyId.value}' not found on any managed DID"))
            case single :: Nil => ZIO.succeed(single)
            case _             =>
              ZIO.fail(
                DIDKeySignerError.KeyNotFound(
                  s"Key '${keyId.value}' is present on multiple managed DIDs; specify DID explicitly"
                )
              )
          }
        } yield result
    }

  private def resolveKey(
      did: CanonicalPrismDID,
      keyId: KeyId
  ): ZIO[WalletAccessContext, DIDKeySignerError.KeyNotFound, Secp256k1KeyPair] =
    managedDIDService
      .findDIDKeyPair(did, keyId)
      .flatMap {
        case Some(key: Secp256k1KeyPair) => ZIO.succeed(key)
        case Some(_)                     =>
          ZIO.fail(DIDKeySignerError.KeyNotFound(s"Key '${keyId.value}' is not secp256k1"))
        case None =>
          ZIO.fail(DIDKeySignerError.KeyNotFound(s"Key '${keyId.value}' not found on DID ${did.toString}"))
      }

  private def ensureDidActive(
      did: CanonicalPrismDID
  ): ZIO[WalletAccessContext, DIDKeySignerError.DIDDeactivated, Unit] =
    managedDIDService
      .isDidDeactivated(did)
      .mapError(err => DIDKeySignerError.DIDDeactivated(err.toString))
      .flatMap { deactivated =>
        ZIO
          .fail(DIDKeySignerError.DIDDeactivated(s"DID ${did.toString} is deactivated"))
          .when(deactivated)
          .unit
      }
}

object DIDKeySignerImpl {
  val layer: URLayer[ManagedDIDService, DIDKeySigner] =
    ZLayer.fromFunction(DIDKeySignerImpl(_))
}
