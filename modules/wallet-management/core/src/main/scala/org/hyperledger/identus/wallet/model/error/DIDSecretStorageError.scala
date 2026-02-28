package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.didcomm.model.DidId
import org.hyperledger.identus.shared.models.*

sealed trait DIDSecretStorageError(
    override val statusCode: StatusCode,
    override val userFacingMessage: String
) extends Failure {
  override val namespace: String = "DIDSecretStorageError"
}

object DIDSecretStorageError {
  case class KeyNotFoundError(didId: DidId, keyId: KeyId)
      extends DIDSecretStorageError(
        StatusCode.NotFound,
        s"The not found: keyId='$keyId', didId='$didId'"
      )
  case class WalletNotFoundError(didId: DidId)
      extends DIDSecretStorageError(
        StatusCode.NotFound,
        s"The DID not Found in Wallet: didId='$didId'"
      )
}
