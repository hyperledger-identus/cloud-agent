package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError}

sealed trait GetManagedDIDError

object GetManagedDIDError {
  final case class WalletStorageError(cause: Throwable) extends GetManagedDIDError // TODO override def toString
  final case class OperationError(cause: DIDOperationError) extends GetManagedDIDError
  final case class ResolutionError(cause: DIDResolutionError) extends GetManagedDIDError
}
