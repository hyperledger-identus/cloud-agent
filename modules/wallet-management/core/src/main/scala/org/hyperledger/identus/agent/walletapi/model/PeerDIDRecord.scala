package org.hyperledger.identus.agent.walletapi.model

import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.models.WalletId

import java.time.Instant

case class PeerDIDRecord(did: DidId, createdAt: Instant, walletId: WalletId)
