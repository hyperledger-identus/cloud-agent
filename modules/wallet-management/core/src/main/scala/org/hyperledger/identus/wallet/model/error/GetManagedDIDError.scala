package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError}
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait GetManagedDIDError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Throwable
    with Failure {
  override val namespace: String = "GetManagedDIDError"
}

object GetManagedDIDError {
  final case class WalletStorageError(cause: Throwable)
      extends GetManagedDIDError(StatusCode.InternalServerError, s"Wallet storage error: ${cause.getMessage}")
  final case class OperationError(cause: DIDOperationError)
      extends GetManagedDIDError(StatusCode.InternalServerError, s"DID operation error: ${cause.toString}")
  final case class ResolutionError(cause: DIDResolutionError)
      extends GetManagedDIDError(StatusCode.InternalServerError, s"DID resolution error: ${cause.toString}")
}
