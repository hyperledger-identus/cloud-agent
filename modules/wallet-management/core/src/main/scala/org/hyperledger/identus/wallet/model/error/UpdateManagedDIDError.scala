package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError, OperationValidationError}
import org.hyperledger.identus.did.core.model.error as didError

sealed trait UpdateManagedDIDError

object UpdateManagedDIDError {
  final case class DIDNotFound(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DIDNotPublished(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DIDAlreadyDeactivated(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class InvalidArgument(msg: String) extends UpdateManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends UpdateManagedDIDError
  final case class OperationError(cause: didError.DIDOperationError) extends UpdateManagedDIDError
  final case class InvalidOperation(cause: didError.OperationValidationError) extends UpdateManagedDIDError
  final case class ResolutionError(cause: didError.DIDResolutionError) extends UpdateManagedDIDError
  final case class CryptographyError(cause: Throwable) extends UpdateManagedDIDError
  final case class MultipleInflightUpdateNotAllowed(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DataIntegrityError(msg: String) extends UpdateManagedDIDError
}
