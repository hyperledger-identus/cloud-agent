package org.hyperledger.identus.did.core.repository

import org.hyperledger.identus.did.core.model.did.PrismDID

trait DIDOperationRepository[F[_]] {
  def getConfirmedPublishedDIDOperations(did: PrismDID): F[Unit]
}
