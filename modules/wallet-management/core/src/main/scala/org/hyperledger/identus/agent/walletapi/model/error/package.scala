package org.hyperledger.identus.agent.walletapi.model

import org.hyperledger.identus.did.core.model.error.{DIDOperationError, DIDResolutionError}

package object error {
  final case class CommonWalletStorageError(cause: Throwable)
  final case class CommonCryptographyError(cause: Throwable)

  given Conversion[CommonWalletStorageError, PublishManagedDIDError] = e =>
    PublishManagedDIDError.WalletStorageError(e.cause)
  given Conversion[CommonCryptographyError, PublishManagedDIDError] = e =>
    PublishManagedDIDError.CryptographyError(e.cause)
  given Conversion[DIDOperationError, PublishManagedDIDError] = PublishManagedDIDError.OperationError(_)

  given Conversion[CommonWalletStorageError, GetManagedDIDError] = e => GetManagedDIDError.WalletStorageError(e.cause)
  given Conversion[DIDOperationError, GetManagedDIDError] = GetManagedDIDError.OperationError(_)
  given Conversion[DIDResolutionError, GetManagedDIDError] = GetManagedDIDError.ResolutionError(_)

  given Conversion[CommonWalletStorageError, UpdateManagedDIDError] = e =>
    UpdateManagedDIDError.WalletStorageError(e.cause)
  given Conversion[CommonCryptographyError, UpdateManagedDIDError] = e =>
    UpdateManagedDIDError.CryptographyError(e.cause)
  given Conversion[DIDOperationError, UpdateManagedDIDError] = UpdateManagedDIDError.OperationError(_)
}
