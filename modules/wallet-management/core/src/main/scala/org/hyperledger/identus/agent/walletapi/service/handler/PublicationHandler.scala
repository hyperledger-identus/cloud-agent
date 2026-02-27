package org.hyperledger.identus.agent.walletapi.service.handler

import org.hyperledger.identus.agent.walletapi.model.error.{CommonCryptographyError, CommonWalletStorageError}
import org.hyperledger.identus.agent.walletapi.model.ManagedDIDState
import org.hyperledger.identus.agent.walletapi.util.KeyResolver
import org.hyperledger.identus.castor.core.model.did.{
  PrismDIDOperation,
  ScheduleDIDOperationOutcome,
  SignedPrismDIDOperation
}
import org.hyperledger.identus.castor.core.model.error.DIDOperationError
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.shared.crypto.Secp256k1KeyPair
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

class PublicationHandler(didService: DIDService, keyResolver: KeyResolver)(masterKeyId: String) {
  def signOperationWithMasterKey[E](state: ManagedDIDState, operation: PrismDIDOperation)(using
      c1: Conversion[CommonWalletStorageError, E],
      c2: Conversion[CommonCryptographyError, E]
  ): ZIO[WalletAccessContext, E, SignedPrismDIDOperation] = {
    for {
      masterKeyPair <-
        keyResolver
          .getKey(state.did, KeyId(masterKeyId))
          .someOrFail(Exception("master-key must exists in the wallet for signing DID operation and submit to Node"))
          .collect(Exception("master-key must be secp256k1 key")) { case keyPair: Secp256k1KeyPair => keyPair }
          .orDie
      signedOperation <- ZIO
        .succeed(masterKeyPair.privateKey.sign(operation.toAtalaOperation.toByteArray))
        .map(signature =>
          SignedPrismDIDOperation(
            operation = operation,
            signature = ArraySeq.from(signature),
            signedWithKey = masterKeyId
          )
        )
    } yield signedOperation
  }

  def submitSignedOperation[E](
      signedOperation: SignedPrismDIDOperation
  )(using c1: Conversion[DIDOperationError, E]): IO[E, ScheduleDIDOperationOutcome] =
    didService.scheduleOperation(signedOperation).mapError[E](e => e)
}
