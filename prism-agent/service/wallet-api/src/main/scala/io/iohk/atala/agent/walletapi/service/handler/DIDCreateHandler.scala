package io.iohk.atala.agent.walletapi.service.handler

import io.iohk.atala.agent.walletapi.crypto.Apollo
import io.iohk.atala.agent.walletapi.model.CreateDIDHdKey
import io.iohk.atala.agent.walletapi.model.ManagedDIDState
import io.iohk.atala.agent.walletapi.model.ManagedDIDTemplate
import io.iohk.atala.agent.walletapi.model.PublicationState
import io.iohk.atala.agent.walletapi.model.WalletSeed
import io.iohk.atala.agent.walletapi.model.error.CreateManagedDIDError
import io.iohk.atala.agent.walletapi.storage.DIDNonSecretStorage
import io.iohk.atala.agent.walletapi.storage.WalletSecretStorage
import io.iohk.atala.agent.walletapi.util.OperationFactory
import io.iohk.atala.castor.core.model.did.PrismDIDOperation
import io.iohk.atala.shared.models.WalletAccessContext
import zio.*

private[walletapi] class DIDCreateHandler(
    apollo: Apollo,
    nonSecretStorage: DIDNonSecretStorage,
    walletSecretStorage: WalletSecretStorage,
)(
    masterKeyId: String
) {
  def materialize(
      didTemplate: ManagedDIDTemplate
  ): ZIO[WalletAccessContext, CreateManagedDIDError, DIDCreateMaterial] = {
    val operationFactory = OperationFactory(apollo)
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      seed <- walletSecretStorage.getWalletSeed
        .someOrElseZIO(ZIO.dieMessage(s"Wallet seed for wallet $walletId does not exist"))
        .mapError(CreateManagedDIDError.WalletStorageError.apply)
      didIndex <- nonSecretStorage
        .getMaxDIDIndex()
        .mapBoth(
          CreateManagedDIDError.WalletStorageError.apply,
          maybeIdx => maybeIdx.map(_ + 1).getOrElse(0)
        )
      generated <- operationFactory.makeCreateOperationHdKey(masterKeyId, seed.toByteArray)(didIndex, didTemplate)
      (createOperation, hdKey) = generated
      state = ManagedDIDState(createOperation, didIndex, PublicationState.Created())
    } yield DIDCreateMaterialImpl(nonSecretStorage)(createOperation, state, hdKey)
  }
}

private[walletapi] trait DIDCreateMaterial {
  def operation: PrismDIDOperation.Create
  def state: ManagedDIDState
  def persist: RIO[WalletAccessContext, Unit]
}

private[walletapi] class DIDCreateMaterialImpl(nonSecretStorage: DIDNonSecretStorage)(
    val operation: PrismDIDOperation.Create,
    val state: ManagedDIDState,
    hdKey: CreateDIDHdKey
) extends DIDCreateMaterial {
  def persist: RIO[WalletAccessContext, Unit] = {
    val did = operation.did
    for {
      _ <- nonSecretStorage
        .insertManagedDID(did, state, hdKey.keyPaths ++ hdKey.internalKeyPaths)
        .mapError(CreateManagedDIDError.WalletStorageError.apply)
    } yield ()
  }
}
