package io.iohk.atala.castor.core.model.did

import io.iohk.atala.shared.models.HexStrings.*

sealed trait PublishedDIDOperation

object PublishedDIDOperation {
  final case class Create(
      updateCommitment: HexString,
      recoveryCommitment: HexString,
      storage: DIDStorage.Cardano,
      document: DIDDocument
  ) extends PublishedDIDOperation
}

final case class PublishedDIDOperationOutcome(
    did: PrismDID,
    operation: PublishedDIDOperation,
    operationId: HexString
)
