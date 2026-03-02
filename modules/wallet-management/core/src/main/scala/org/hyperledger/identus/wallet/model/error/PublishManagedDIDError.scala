package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.did.PrismDID
import org.hyperledger.identus.did.core.model.error.DIDOperationError
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait PublishManagedDIDError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Throwable
    with Failure {
  override val namespace: String = "PublishManagedDIDError"
}

object PublishManagedDIDError {
  final case class DIDNotFound(did: PrismDID)
      extends PublishManagedDIDError(StatusCode.NotFound, s"DID not found: $did")
  final case class WalletStorageError(cause: Throwable)
      extends PublishManagedDIDError(StatusCode.InternalServerError, s"Wallet storage error: ${cause.getMessage}")
  final case class OperationError(cause: DIDOperationError)
      extends PublishManagedDIDError(StatusCode.InternalServerError, s"DID operation error: ${cause.toString}")
  final case class CryptographyError(cause: Throwable)
      extends PublishManagedDIDError(StatusCode.InternalServerError, s"Cryptography error: ${cause.toString}")
}
