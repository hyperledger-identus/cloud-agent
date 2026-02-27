package org.hyperledger.identus.agent.walletapi.model.error

import org.hyperledger.identus.castor.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.castor.core.model.error.{DIDOperationError, DIDResolutionError, OperationValidationError}
import org.hyperledger.identus.castor.core.model.error as castor

sealed trait UpdateManagedDIDError

object UpdateManagedDIDError {
  final case class DIDNotFound(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DIDNotPublished(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DIDAlreadyDeactivated(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class InvalidArgument(msg: String) extends UpdateManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends UpdateManagedDIDError
  final case class OperationError(cause: castor.DIDOperationError) extends UpdateManagedDIDError
  final case class InvalidOperation(cause: castor.OperationValidationError) extends UpdateManagedDIDError
  final case class ResolutionError(cause: castor.DIDResolutionError) extends UpdateManagedDIDError
  final case class CryptographyError(cause: Throwable) extends UpdateManagedDIDError
  final case class MultipleInflightUpdateNotAllowed(did: CanonicalPrismDID) extends UpdateManagedDIDError
  final case class DataIntegrityError(msg: String) extends UpdateManagedDIDError
}
