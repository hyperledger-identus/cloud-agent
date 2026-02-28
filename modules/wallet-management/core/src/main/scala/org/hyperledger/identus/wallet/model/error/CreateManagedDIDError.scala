package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.error.OperationValidationError
import org.hyperledger.identus.did.core.model.error as didError

sealed trait CreateManagedDIDError extends Throwable

object CreateManagedDIDError {
  final case class InvalidArgument(msg: String) extends CreateManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends CreateManagedDIDError
  final case class InvalidOperation(cause: didError.OperationValidationError) extends CreateManagedDIDError
}
