package org.hyperledger.identus.wallet.model.error

import org.hyperledger.identus.did.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError, OperationValidationError}
import org.hyperledger.identus.did.core.model.error as didError
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait UpdateManagedDIDError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Throwable
    with Failure {
  override val namespace: String = "UpdateManagedDIDError"
}

object UpdateManagedDIDError {
  final case class DIDNotFound(did: CanonicalPrismDID)
      extends UpdateManagedDIDError(StatusCode.NotFound, s"DID not found: $did")
  final case class DIDNotPublished(did: CanonicalPrismDID)
      extends UpdateManagedDIDError(StatusCode.Conflict, s"DID not published: $did")
  final case class DIDAlreadyDeactivated(did: CanonicalPrismDID)
      extends UpdateManagedDIDError(StatusCode.Conflict, s"DID already deactivated: $did")
  final case class InvalidArgument(msg: String)
      extends UpdateManagedDIDError(StatusCode.BadRequest, s"Invalid argument: $msg")
  final case class WalletStorageError(cause: Throwable)
      extends UpdateManagedDIDError(StatusCode.InternalServerError, s"Wallet storage error: ${cause.getMessage}")
  final case class OperationError(cause: didError.DIDOperationError)
      extends UpdateManagedDIDError(StatusCode.InternalServerError, s"DID operation error: ${cause.toString}")
  final case class InvalidOperation(cause: didError.OperationValidationError)
      extends UpdateManagedDIDError(StatusCode.UnprocessableContent, s"Invalid operation: ${cause.toString}")
  final case class ResolutionError(cause: didError.DIDResolutionError)
      extends UpdateManagedDIDError(StatusCode.InternalServerError, s"DID resolution error: ${cause.toString}")
  final case class CryptographyError(cause: Throwable)
      extends UpdateManagedDIDError(StatusCode.InternalServerError, s"Cryptography error: ${cause.toString}")
  final case class MultipleInflightUpdateNotAllowed(did: CanonicalPrismDID)
      extends UpdateManagedDIDError(StatusCode.Conflict, s"Multiple in-flight update operations are not allowed: $did")
  final case class DataIntegrityError(msg: String)
      extends UpdateManagedDIDError(StatusCode.InternalServerError, s"Data integrity error: $msg")
}
