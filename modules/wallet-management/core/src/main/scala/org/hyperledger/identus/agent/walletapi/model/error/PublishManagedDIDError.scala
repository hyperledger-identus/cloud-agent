package org.hyperledger.identus.agent.walletapi.model.error

import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.castor.core.model.error.DIDOperationError

sealed trait PublishManagedDIDError

object PublishManagedDIDError {
  final case class DIDNotFound(did: PrismDID) extends PublishManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends PublishManagedDIDError
  final case class OperationError(cause: DIDOperationError) extends PublishManagedDIDError
  final case class CryptographyError(cause: Throwable) extends PublishManagedDIDError
}
