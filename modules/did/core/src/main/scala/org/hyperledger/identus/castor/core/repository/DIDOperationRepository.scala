package org.hyperledger.identus.castor.core.repository

import org.hyperledger.identus.castor.core.model.did.PrismDID

trait DIDOperationRepository[F[_]] {
  def getConfirmedPublishedDIDOperations(did: PrismDID): F[Unit]
}
