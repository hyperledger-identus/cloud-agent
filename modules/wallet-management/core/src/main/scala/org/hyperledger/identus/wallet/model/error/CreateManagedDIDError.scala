package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.error.OperationValidationError
import org.hyperledger.identus.did.core.model.error as didError
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait CreateManagedDIDError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Throwable
    with Failure {
  override val namespace: String = "CreateManagedDIDError"
}

object CreateManagedDIDError {
  final case class InvalidArgument(msg: String)
      extends CreateManagedDIDError(StatusCode.UnprocessableContent, s"Invalid argument: $msg")
  final case class WalletStorageError(cause: Throwable)
      extends CreateManagedDIDError(StatusCode.InternalServerError, s"Wallet storage error: ${cause.getMessage}")
  final case class InvalidOperation(cause: didError.OperationValidationError)
      extends CreateManagedDIDError(StatusCode.UnprocessableContent, s"Invalid operation: ${cause.toString}")
}
