package org.hyperledger.identus.did.api

import org.hyperledger.identus.did.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.ZIO

final case class DIDSigningContext(
    did: CanonicalPrismDID,
    keyId: KeyId,
    keyPair: Secp256k1KeyPair
)

sealed trait DIDKeySignerError

object DIDKeySignerError {
  final case class KeyNotFound(message: String) extends DIDKeySignerError
  final case class DIDDeactivated(message: String) extends DIDKeySignerError
  final case class AmbiguousDID(message: String) extends DIDKeySignerError
}

trait DIDKeySigner {

  /** Resolve the DID, key pair, and validate the DID is active.
    *
    * @param didKeyId
    *   Optional key identifier, either a plain keyId or "did:prism:suffix#keyId" format
    * @param defaultKeyId
    *   Default key ID to use when didKeyId is None
    * @param maxScan
    *   Maximum number of managed DIDs to scan when no explicit DID is provided
    */
  def resolveSigningKey(
      didKeyId: Option[String],
      defaultKeyId: KeyId,
      maxScan: Int = 200
  ): ZIO[WalletAccessContext, DIDKeySignerError, DIDSigningContext]
}
