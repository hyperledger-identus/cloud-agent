package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.error.CommonWalletStorageError
import org.hyperledger.identus.agent.walletapi.model.ManagedDIDDetail
import org.hyperledger.identus.agent.walletapi.storage.{DIDNonSecretStorage, DIDSecretStorage, WalletSecretStorage}
import org.hyperledger.identus.castor.core.model.did.CanonicalPrismDID
import org.hyperledger.identus.castor.core.model.error.DIDOperationError
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.castor.core.util.DIDOperationValidator
import org.hyperledger.identus.event.notification.{Event, EventNotificationService}
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

class ManagedDIDServiceWithEventNotificationImpl(
    didService: DIDService,
    didOpValidator: DIDOperationValidator,
    override private[walletapi] val secretStorage: DIDSecretStorage,
    override private[walletapi] val nonSecretStorage: DIDNonSecretStorage,
    walletSecretStorage: WalletSecretStorage,
    apollo: Apollo,
    eventNotificationService: EventNotificationService
) extends ManagedDIDServiceImpl(
      didService,
      didOpValidator,
      secretStorage,
      nonSecretStorage,
      walletSecretStorage,
      apollo
    ) {

  private val didStatusUpdatedEventName = "DIDStatusUpdated"

  override protected def computeNewDIDStateFromDLTAndPersist[E](
      did: CanonicalPrismDID
  )(using
      c1: Conversion[CommonWalletStorageError, E],
      c2: Conversion[DIDOperationError, E]
  ): ZIO[WalletAccessContext, E, Boolean] = {
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      updated <- super.computeNewDIDStateFromDLTAndPersist(did)
      _ <- ZIO.when(updated) {
        val result = for {
          maybeUpdatedDID <- nonSecretStorage.getManagedDIDState(did)
          updatedDID <- ZIO.fromOption(maybeUpdatedDID)
          producer <- eventNotificationService.producer[ManagedDIDDetail]("DIDDetail")
          _ <- producer.send(Event(didStatusUpdatedEventName, ManagedDIDDetail(did, updatedDID), walletId))
        } yield ()
        result.catchAll(e => ZIO.logError(s"Notification service error: $e"))
      }
    } yield updated
  }
}

object ManagedDIDServiceWithEventNotificationImpl {
  val layer: RLayer[
    DIDOperationValidator & DIDService & DIDSecretStorage & DIDNonSecretStorage & WalletSecretStorage & Apollo &
      EventNotificationService,
    ManagedDIDService
  ] = ZLayer.fromZIO {
    for {
      didService <- ZIO.service[DIDService]
      didOpValidator <- ZIO.service[DIDOperationValidator]
      secretStorage <- ZIO.service[DIDSecretStorage]
      nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
      walletSecretStorage <- ZIO.service[WalletSecretStorage]
      apollo <- ZIO.service[Apollo]
      createDIDSem <- Semaphore.make(1)
      eventNotificationService <- ZIO.service[EventNotificationService]
    } yield ManagedDIDServiceWithEventNotificationImpl(
      didService,
      didOpValidator,
      secretStorage,
      nonSecretStorage,
      walletSecretStorage,
      apollo,
      eventNotificationService
    )
  }
}
