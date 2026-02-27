package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.anoncreds.AnoncredLinkSecretWithId
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.URIO

trait LinkSecretService {
  def fetchOrCreate(): URIO[WalletAccessContext, AnoncredLinkSecretWithId]
}
