package org.hyperledger.identus.credentials.core.service

import org.hyperledger.identus.credentials.anoncreds.AnoncredLinkSecretWithId
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.URIO

trait LinkSecretService {
  def fetchOrCreate(): URIO[WalletAccessContext, AnoncredLinkSecretWithId]
}
