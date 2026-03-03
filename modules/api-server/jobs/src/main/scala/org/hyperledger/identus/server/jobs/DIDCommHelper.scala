package org.hyperledger.identus.server.jobs

import org.hyperledger.identus.didcomm.{AgentPeerService, DidAgent}
import org.hyperledger.identus.didcomm.model.DidId
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.wallet.model.error.DIDSecretStorageError.{KeyNotFoundError, WalletNotFoundError}
import org.hyperledger.identus.wallet.service.ManagedDIDService
import org.hyperledger.identus.wallet.storage.DIDNonSecretStorage
import zio.{ZIO, ZLayer}

trait DIDCommHelper {

  def buildDIDCommAgent(
      myDid: DidId
  ): ZIO[ManagedDIDService & WalletAccessContext, KeyNotFoundError, ZLayer[Any, Nothing, DidAgent]] = {
    for {
      managedDidService <- ZIO.service[ManagedDIDService]
      peerDID <- managedDidService.getPeerDID(myDid)
      agent = AgentPeerService.makeLayer(peerDID)
    } yield agent
  }

  def buildWalletAccessContextLayer(
      myDid: DidId
  ): ZIO[DIDNonSecretStorage, WalletNotFoundError, WalletAccessContext] = {
    for {
      nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
      maybePeerDIDRecord <- nonSecretStorage.getPeerDIDRecord(myDid).orDie
      peerDIDRecord <- ZIO.fromOption(maybePeerDIDRecord).mapError(_ => WalletNotFoundError(myDid))
      _ <- ZIO.logInfo(s"PeerDID record successfully loaded in DIDComm receiver endpoint: $peerDIDRecord")
      walletAccessContext = WalletAccessContext(peerDIDRecord.walletId)
    } yield walletAccessContext
  }
}
