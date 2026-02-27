package org.hyperledger.identus.agent.walletapi.storage

import org.hyperledger.identus.agent.walletapi.model.{
  DIDPublicKeyTemplate,
  DIDUpdateLineage,
  ManagedDIDState,
  ManagedDIDTemplate,
  PublicationState,
  Wallet
}
import org.hyperledger.identus.agent.walletapi.service.WalletManagementService
import org.hyperledger.identus.agent.walletapi.util.OperationFactory
import org.hyperledger.identus.castor.core.model.did.{
  EllipticCurve,
  PrismDID,
  PrismDIDOperation,
  ScheduledDIDOperationStatus,
  VerificationRelationship
}
import org.hyperledger.identus.shared.crypto.ApolloSpecHelper
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletAdministrationContext}
import zio.*
import zio.test.*

import java.time.Instant
import scala.collection.immutable.ArraySeq

trait StorageSpecHelper extends ApolloSpecHelper {
  protected val didExample = PrismDID.buildLongFormFromOperation(PrismDIDOperation.Create(Nil, Nil, Nil))

  protected def updateLineage(
      operationId: Array[Byte] = Array.fill(32)(0),
      operationHash: Array[Byte] = Array.fill(32)(0),
      status: ScheduledDIDOperationStatus = ScheduledDIDOperationStatus.Confirmed
  ) = DIDUpdateLineage(
    operationId = ArraySeq.from(operationId),
    operationHash = ArraySeq.from(operationHash),
    previousOperationHash = ArraySeq.fill(32)(0),
    status = status,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH
  )

  protected def generateKeyPair() = apollo.secp256k1.generateKeyPair

  protected def generateCreateOperation(keyIds: Seq[String], didIndex: Int) =
    OperationFactory(apollo).makeCreateOperation(KeyId("master0"), Array.fill(64)(0))(
      didIndex,
      ManagedDIDTemplate(
        publicKeys =
          keyIds.map(DIDPublicKeyTemplate(_, VerificationRelationship.Authentication, EllipticCurve.SECP256K1)),
        services = Nil,
        contexts = Nil
      )
    )

  protected def initializeDIDStateAndKeys(keyIds: Seq[String] = Nil, didIndex: Int) = {
    for {
      nonSecretStorage <- ZIO.service[DIDNonSecretStorage]
      generated <- generateCreateOperation(keyIds, didIndex)
      (createOperation, keys) = generated
      did = createOperation.did
      _ <- nonSecretStorage.insertManagedDID(
        did,
        ManagedDIDState(createOperation, didIndex, PublicationState.Created()),
        keys.hdKeys,
        keys.randKeyMeta
      )
    } yield did
  }

  extension [R, E](spec: Spec[R & WalletAccessContext, E]) {
    def globalWallet: Spec[R & WalletManagementService, E] = {
      spec.provideSomeLayer(
        ZLayer.fromZIO(
          ZIO
            .serviceWithZIO[WalletManagementService](_.createWallet(Wallet("global-wallet")))
            .map(wallet => WalletAccessContext(wallet.id))
            .orDieAsUnmanagedFailure
            .provideSomeLayer(ZLayer.succeed(WalletAdministrationContext.Admin()))
        )
      )
    }
  }
}
