package org.hyperledger.identus.agent.walletapi.model.error

import org.hyperledger.identus.castor.core.model.error.OperationValidationError
import org.hyperledger.identus.castor.core.model.error as castor

sealed trait CreateManagedDIDError extends Throwable

object CreateManagedDIDError {
  final case class InvalidArgument(msg: String) extends CreateManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends CreateManagedDIDError
  final case class InvalidOperation(cause: castor.OperationValidationError) extends CreateManagedDIDError
}
