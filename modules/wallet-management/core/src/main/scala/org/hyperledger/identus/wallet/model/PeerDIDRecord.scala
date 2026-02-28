package org.hyperledger.identus.wallet.model

import org.hyperledger.identus.didcomm.model.DidId
import org.hyperledger.identus.shared.models.WalletId

import java.time.Instant

case class PeerDIDRecord(did: DidId, createdAt: Instant, walletId: WalletId)
